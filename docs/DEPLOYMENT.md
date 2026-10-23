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
| `GOOGLE_CLIENT_ID` | 구글 OAuth 클라이언트 ID(웹 애플리케이션) |
| `GOOGLE_CLIENT_SECRET` | 구글 OAuth 클라이언트 보안 비밀 |
| `ADMIN_KAKAO_IDS` | 운영자 카카오 회원번호(쉼표 구분) |
| `ADMIN_GOOGLE_IDS` | 운영자 구글 `sub`(쉼표 구분, 선택) |

값을 바꾼 뒤 서버에서 `sudo /opt/chak/run.sh` 를 실행하거나(SSM Session Manager), 다음 이미지 배포 때 반영된다.

### 2-1. 찰떡 충전(토스페이먼츠)
기본은 `CHALTTEOK_PROVIDER=none`이라 충전이 `503`으로 거부된다. 토스 연동 후 실제 충전을 열려면 서버 환경변수에 `CHALTTEOK_PROVIDER=toss`와 `TOSS_SECRET_KEY`(토스 시크릿 키, 테스트는 `test_gsk_…`)가 필요하다. 시연용 가짜 충전은 `mock`. 프론트(Vercel)에는 클라이언트 키 `VITE_TOSS_CLIENT_KEY`와 `VITE_CHALTTEOK_API=server`를 넣는다. 현재 CloudFormation 템플릿은 이 두 값을 아직 시크릿/환경변수로 주입하지 않는다(스택 업데이트 필요).

### 2-2. 오류 알림(디스코드)
서버 오류(처리되지 않은 500, 카카오·구글·토스 연동 오류)와 사용자 브라우저에서 난 오류(`POST /api/public/client-errors`)를 디스코드로 받는다.
1. 디스코드 서버에 알림 채널을 만들고 **채널 설정 → 연동 → 웹후크 → 새 웹후크**를 만든 뒤 URL을 복사한다.
2. Secrets Manager `chak/prod/app`에 `DISCORD_WEBHOOK_URL`(위 URL)을 추가한다. 값은 `https://discord.com/api/webhooks/`로 시작해야 하며, 다른 주소면 서버가 기동하지 않는다.
3. 앱을 다시 시작하면 반영된다(`sudo /opt/chak/run.sh` 또는 다음 배포).
- 값이 비어 있으면 알림은 서버 로그에만 남는다.

### 2-2-1. 시크릿 값을 앱이 직접 읽는다
prod 프로필의 앱은 시작할 때 Secrets Manager의 `chak/prod/app`(`CHAK_SECRETS_ID`로 변경 가능)을 읽어 **모든 문자열 키를 설정 값으로 추가**한다(`SecretsManagerEnvironmentPostProcessor`). 그래서 새 키는 시크릿에만 넣으면 되고 서버의 `run.sh`를 고칠 필요가 없다.
- 환경변수·명령행 값이 항상 우선한다. 시크릿 값은 가장 낮은 우선순위다.
- 서버(EC2)의 인스턴스 역할이 이미 이 시크릿을 읽을 권한을 갖고 있다(`secretsmanager:GetSecretValue`).
- 읽지 못해도(권한·네트워크) 기동은 계속하고 경고만 남기며, 값은 로그에 남기지 않는다. 로그에서 `Loaded N keys from Secrets Manager`로 확인할 수 있다.
- 로컬·테스트에서는 `chak.secrets.id`가 비어 있어 동작하지 않는다.

### 2-3. 서버 생존 감시(업타임)
외부 업타임 서비스가 `https://www.chak.live/api/public/health`를 5분마다 확인하고, 실패하면 디스코드로 알린다(서버가 통째로 꺼진 경우는 앱이 스스로 알릴 수 없다).
- UptimeRobot(무료)이나 Better Stack에서 HTTP 모니터를 만들고 URL과 확인 주기(5분)를 넣은 뒤, 알림 채널로 디스코드를 연결한다.

### 3. GHCR 이미지 공개
첫 배포 후 GitHub → 조직의 Packages → `14-demoday-backend-chak` → Package settings → **Change visibility → Public**. 서버가 인증 없이 이미지를 받는다(코드는 공개 저장소라 이미지에 비밀이 없다. 비밀은 실행 시 Secrets Manager에서 읽는다).

### 4. 프론트 연결
프론트(Vercel)는 `/api/*` 요청을 CloudFront 주소로 넘기는 rewrite를 쓴다. 브라우저에서는 같은 사이트라 쿠키(`SameSite=Lax`)와 CORS 문제가 없다. 카카오 개발자 콘솔의 Redirect URI에 `https://<프론트 도메인>/auth/kakao/callback`, 구글 클라우드 콘솔의 승인된 리디렉션 URI에 `https://<프론트 도메인>/auth/google/callback`을 등록한다.

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

### DB 비밀번호 자동 교체 (2026-10-22 장애)

RDS의 관리형 비밀번호(`rds!db-...` 시크릿, `ManageMasterUserPassword`)는 **7일마다 자동으로 바뀐다.** 서버 실행 스크립트(`run.sh`)는 컨테이너를 시작할 때 한 번만 비밀번호를 환경변수로 넘기므로, 교체 뒤에는 낡은 비밀번호로 새 DB 연결이 전부 실패해 `CannotCreateTransactionException`이 쏟아졌다(RDS는 정상, 연결 수 0). 또 이미지가 같으면 `update.sh`가 컨테이너를 다시 띄우지 않아서 같은 코드를 다시 배포해도 복구되지 않았다.

지금은 앱이 직접 처리한다.
- 시작할 때 `chak.db-secret-id`(기본값은 현재 RDS 시크릿 이름, `DB_SECRET_ID`로 재정의)의 최신 계정 정보를 읽어 환경변수의 `DB_PASSWORD`보다 우선해 쓴다. 읽지 못하면 환경변수 값으로 계속한다.
- 실행 중에는 5분마다 시크릿을 확인해, 비밀번호가 바뀌었으면 연결 풀을 새 값으로 갱신한다(재시작 불필요).
- DB를 새로 만들어 시크릿 이름이 바뀌면 `DB_SECRET_ID`(또는 `chak/prod/app` 시크릿의 같은 키)를 새 이름으로 맞춘다.

**증상으로 확인하기:** 헬스체크는 `UP`인데 DB를 쓰는 API가 멈추고 RDS 연결 수가 0이면 이 경우를 먼저 의심한다. RDS → 시크릿의 "마지막 교체 날짜"가 장애 시작 시각 직전인지 본다. 이전 코드에서는 수동으로 `sudo /opt/chak/run.sh`를 실행하면 복구된다.

## 비용(시드니 리전, 대략)
EC2 t3.small ≈ $19, RDS db.t4g.micro + 20GB ≈ $19, 공인 IPv4 ≈ $4, 시크릿 2개 ≈ $1, CloudFront·S3는 소량이면 거의 무료 → **월 약 $40~45**. 계정의 무료 플랜 크레딧이 소진되면 청구된다.

## 알아둘 점
- **리전**: 이 계정은 시드니(`ap-southeast-2`)로 고정돼 있다(서울은 계정 소유자가 업그레이드해야 사용 가능). 한국 사용자에게는 지연이 약 150ms 늘어난다. 서울로 옮기려면 같은 템플릿을 새 리전에서 만들고 DB를 옮기면 된다.
- **CloudFront → EC2 구간은 HTTP**다(AWS 내부 통신, 보안 그룹이 CloudFront 주소 대역만 허용). 사용자 구간은 HTTPS다. 도메인을 구입하면 ALB/ACM으로 끝까지 암호화할 수 있다.
- 서버 시간대는 `Asia/Seoul`로 고정해(`-Duser.timezone`) 매일 열람 시각이 어긋나지 않게 했다.
- DB는 단일 인스턴스(Multi-AZ 아님)다. 삭제 시 스냅샷을 남긴다(`DeletionPolicy: Snapshot`). 이미지 버킷은 스택을 지워도 남는다(`Retain`).
