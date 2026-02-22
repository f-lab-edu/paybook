# Paybook

주문 / 결제 / 정산 도메인 학습을 위한 멀티모듈 프로젝트

## 기술 스택
- Java 21
- Spring Boot 4.0.3
- Gradle 멀티모듈

## 모듈 구조
| 모듈 | 설명 | 포트 |
|------|------|------|
| core | 공통 도메인, 유틸 | - |
| order | 주문 서비스 | 8080 |
| payment | 결제 서비스 | 8081 |
| settlement | 정산 서비스 | 8082 |

## 실행
```bash
# 주문 서비스
./gradlew :order:bootRun

# 결제 서비스
./gradlew :payment:bootRun

# 정산 서비스
./gradlew :settlement:bootRun
```
