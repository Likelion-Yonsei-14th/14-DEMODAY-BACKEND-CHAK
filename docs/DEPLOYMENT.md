# CHAK 백엔드 배포 가이드

## 구조

```text
GitHub (main 커밋)
   └─ GitHub Actions: 테스트 → 도커 이미지 빌드 → GHCR에 push
                                                  │ (1분마다 확인)
브라우저 ── Vercel(프론트) ──/api/* 프록시──> CloudFront(HTTPS) ──> EC2(Docker) ──> RDS MySQL
                                                                         └────────> S3(이미지, 비공개)
```

- **AWS 자격증명이 GitHub에 필요 없다.** 서버가 GHCR에서 `:latest` 이미지를 1분마다 확인하고, 새 이미지가 있으면 컨테이너를 교체한다. 새 컨테이너가 2분 안에 정상(`/api/public/health`)이 되지 않으면 이전 이미지로 되돌리고, 실패한 이미지는 다시 시도하지 않는다.
- 이 AWS 계정은 조직 정책(SCP)이 GitHub OIDC 연동을 막고 있어 위 방식을 택했다.
- 비밀값(DB 비밀번호, JWT 시크릿)은 AWS가 생성해 Secrets Manager에 둔다. 사람이 입력하지 않는다.

## 처음 한 번 하는 설정

### 1. 인프라 만들기
`deploy/aws/chak-stack.yaml` CloudFormation 스택(`chak-prod`)이 EC2, RDS, S3, CloudFront, IAM 역할, 보안 그룹, 시크릿을 만든다.

```bash
aws cloudformation create-stack --stack-name chak-prod \
  --template-body file://deploy/aws/chak-stack.yaml --capabilities CAPABILITY_IAM \
  --parameters ParameterKey=VpcId,ParameterValue=<vpc-id> \
               ParameterKey=SubnetIds,ParameterValue=\"<subnet-a>,<subnet-b>,<subnet-c>\" \
               ParameterKey=CloudFrontPrefixListId,ParameterValue=<pl-id>
```

- `<pl-id>`: `aws ec2 describe-managed-prefix-lists --filters Name=prefix-list-name,Values=com.amazonaws.global.cloudfront.origin-facing`
- 프론트 주소가 정해지면 `FrontendOrigins` 파라미터(쉼표 구분)로 CORS(API·S3)를 맞춘다(스택 업데이트).
- 결제는 기본이 `PaymentProvider=none`(승인 거부)이다. 시연용 가짜 결제를 켜려면 `mock`으로 바꾼다. **실제 돈이 오가지 않는다.**
- 무료 플랜 계정은 RDS 백업 보관 기간을 1일까지만 허용한다(템플릿에 반영).

### 2. 카카오 키 입력
스택이 `chak/prod/app` 시크릿을 만들고 `JWT_SECRET`을 자동 생성한다. 아래 값은 직접 채운다(Secrets Manager 콘솔 → `chak/prod/app` → 값 수정).

| 키 | 값 |
| --- | --- |
| `KAKAO_CLIENT_ID` | 카카오 REST API 키 |
| `KAKAO_CLIENT_SECRET` | 카카오 클라이언트 시크릿(사용 시) |
| `ADMIN_KAKAO_IDS` | 운영자 카카오 회원번호(쉼표 구분) |

값을 바꾼 뒤 서버에서 `sudo /opt/chak/run.sh` 를 실행하거나(SSM Session Manager), 다음 이미지 배포 때 반영된다.

### 3. GHCR 이미지 공개
첫 배포 후 GitHub → 조직의 Packages → `14-demoday-backend-chak` → Package settings → **Change visibility → Public**. 서버가 인증 없이 이미지를 받는다(코드는 공개 저장소라 이미지에 비밀이 없다. 비밀은 실행 시 Secrets Manager에서 읽는다).

### 4. 프론트 연결
프론트(Vercel)는 `/api/*` 요청을 CloudFront 주소로 넘기는 rewrite를 쓴다. 브라우저에서는 같은 사이트라 쿠키(`SameSite=Lax`)와 CORS 문제가 없다. 카카오 개발자 콘솔의 Redirect URI에 `https://<프론트 도메인>/auth/kakao/callback`을 등록한다.

## 매일 쓰는 흐름
`main`에 머지 → GitHub Actions(`.github/workflows/docker-publish.yml`)가 테스트·빌드·push(약 4~6분) → 서버가 1분 안에 감지해 교체. 문서(`*.md`, `docs/`)만 바뀐 커밋은 배포하지 않는다.

## 서버 확인과 운영

접속은 SSM Session Manager(키 파일·SSH 포트 없음)로 한다. AWS 콘솔 → EC2 → `chak-server` → 연결 → Session Manager.

```bash
docker ps                          # 컨테이너 상태
docker logs -f chak                # 앱 로그
sudo systemctl status chak-update.timer
journalctl -u chak-update.service  # 자동 교체 기록
cat /opt/chak/bad-image            # 롤백된 이미지 ID(있으면)
curl localhost/api/public/health
```

수동 롤백: 이전 이미지로 `docker tag <이전 이미지 ID> ghcr.io/likelion-yonsei-14th/14-demoday-backend-chak:latest` 후 `sudo /opt/chak/run.sh`. (GitHub에서 문제 커밋을 되돌려 머지하는 쪽이 더 안전하다.)

## 비용(시드니 리전, 대략)
EC2 t3.small ≈ $19, RDS db.t4g.micro + 20GB ≈ $19, 공인 IPv4 ≈ $4, 시크릿 2개 ≈ $1, CloudFront·S3는 소량이면 거의 무료 → **월 약 $40~45**. 계정의 무료 플랜 크레딧이 소진되면 청구된다.

## 알아둘 점
- **리전**: 이 계정은 시드니(`ap-southeast-2`)로 고정돼 있다(서울은 계정 소유자가 업그레이드해야 사용 가능). 한국 사용자에게는 지연이 약 150ms 늘어난다. 서울로 옮기려면 같은 템플릿을 새 리전에서 만들고 DB를 옮기면 된다.
- **CloudFront → EC2 구간은 HTTP**다(AWS 내부 통신, 보안 그룹이 CloudFront 주소 대역만 허용). 사용자 구간은 HTTPS다. 도메인을 구입하면 ALB/ACM으로 끝까지 암호화할 수 있다.
- 서버 시간대는 `Asia/Seoul`로 고정해(`-Duser.timezone`) 매일 열람 시각이 어긋나지 않게 했다.
- DB는 단일 인스턴스(Multi-AZ 아님)다. 삭제 시 스냅샷을 남긴다(`DeletionPolicy: Snapshot`). 이미지 버킷은 스택을 지워도 남는다(`Retain`).
