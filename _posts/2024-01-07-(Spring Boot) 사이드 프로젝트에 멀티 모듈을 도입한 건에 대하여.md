---
title: [(Spring Boot) 사이드 프로젝트에 멀티 모듈을 도입한 건에 대하여]
date: 2024-01-07 14:30:44 +09:00
categories: [Spring, 리팩토링]
tags: [Lovebird, 스프링, 멀티모듈]
image: /assets/img/posts/20240106-0.png
---

<br>

> ### 이 포스팅은 2024년 1월 6일 토요일, `Prography 네트워킹 세미나`에서 발표한 내용 입니다. 포스팅과 관련된 코드는 [Lovebird 서버 Github](https://github.com/wooda-ege/lovebird-server)에 저장돼 있습니다.

<br>

## 서론: Lovebird 이야기

&nbsp; 2023년 3월, `프로그라피`(Prography)에서 만난 Lovebird 팀은 '코로나가 점차 수그러들면서 연인 간의 데이트 횟수가 증가할 것이고, 추억을 기록할 수 있는 서비스가 필요할 것이다.'라는 가정에서 Lovebird 앱 개발을 시작하였다. 추억을 기록할 수 있는 수단으로 다이어리를 타임라인에 저장할 수 있는 기능과 지도에 기록할 수 있는 기능을 채택하였고, `v1.0`에서는 다이어리 기능을 개발하기로 하였다.

&nbsp; 그렇게 MVP 개발 후 10월 15일, 첫 배포가 이루어졌다. 우여곡절 끝에 배포에 성공했지만, 수정해야 할 부분이 꽤 있었고, 수정하는 기간 동안 서버 전체 리팩토링을 결정하게 되었다. 이번 포스팅에서는 Lovebird 서버 리팩토링에 대한 전반적인 얘기와 함께 사이드 프로젝트에 멀티 모듈을 도입한 건에 대하여 `STAR 기법`으로 이야기 하려 한다.

<br>

## 상황 (Situation): As-is 분석을 통한 이전 상황 공유

&nbsp; 먼저 As-Is 프로젝트를 살펴보며 리팩토링의 필요성을 느껴보자.

> ### 1. 난장판이 되어버린 코드

![20240106-1.png](/assets/img/posts/20240106-1.png)

&nbsp; Lovebird는 첫 심사부터 승인까지 리젝(reject)을 약 10번 정도 당했다. 마음이 급하다 보니 유의사항을 제대로 체크하지 못했고, 그 과정에서 급하게 코드를 수정한 흔적을 어렵지 않게 찾아볼 수 있었다. 또 에러 핸들링이나 다건 조회 시 페이지네이션 활용 등이 미비했고, 가장 중요하다고 생각하는 코드 포멧팅이나 컨벤션이 제대로 적용돼 있지 않았다.

> ### 2. DB 설계와 ORM 활용의 문제

&nbsp; 초기 DB를 설계할 때 논리적 외래 키(logical foreign key)는 설정하되, `물리적 외래 키 (physical foreign key)는 설정하지 않고`, 비즈니스 로직을 통해 제약을 걸기로 결정했다. 편하게 개발하기 위해 이렇게 설계했지만, 정합성을 위해 작성하는 로직이 생각보다 너무 많고 복잡하여 이른 시간에 후회하게 되었다.

&nbsp; 또 프로젝트 초기에 JPA에 대한 지식이 깊지 않아 `@CollectionTable` 등의 어노테이션을 활용하여 무심결에 외래 키를 사용하고, Lazy Init 에러가 발생할 때마다 `Eager Loading`으로 설정하여 성능을 저하시킨 부분도 많았다.

> ### 3. 서버 분리의 문제

![20240106-2.png](/assets/img/posts/20240106-2.png)

&nbsp; 마지막은 서버 분리의 문제이다. Production 환경에서 (External Api까진 아니더라도) Internal Api 서버와 Batch 서버를 분리하고 싶었다. 이 분리 과정에서 프로젝트가 두 개가 되는데, 만약 Entity 클래스에 작은 수정이 생기면 두 프로젝트를 모두 수정해야 하는, 작업을 두 번 하는 상황이 발생했다. 만약 깜빡하여 수정하지 않는다면 동작에 크리티컬 한 영향을 줄 가능성 또한 존재했다.

&nbsp; 앞선 코드 문제나 DB 문제의 경우, 코드를 전체 새로 작성하기로 결정했고, 유저가 많지 않은 상황에서 DB 마이그레이션이 크게 번거롭지 않기 때문에 큰 문제가 아니었지만, 서버 분리의 문제를 해결하기 위해선 새로운 무언가를 도입해야만 했다.

## 문제 (Task): 멀티모듈 도입과 문제

> ### Nexus Repository를 사용하면 어떨까?

![20240106-3.png](/assets/img/posts/20240106-3.png)

&nbsp; 처음 생각한 방법은 Nexus Repository를 사용하는 것이다. `Nexus`란 Sonatype에서 만든 저장소 관리자 프로젝트다. Nexus를 통해 우리는 시스템 적으로 일관성을 보장 받을 수 있고, 이전 회사에서 활용한 경험이 있었다.

&nbsp; 하지만 문제가 있었다. 바로 개발 사이클이 너무 번거로워 진다는 것이다. 하나의 기능을 개발하기 위해 여러 프로젝트를 뒤적거려야 했고, 이전 회사에서 활용할 때는 Nexus 배포 딜레이로 인해 시간을 허비한 경험도 있었다.

> ### 멀티 모듈 도입과 문제

![20240106-4.png](/assets/img/posts/20240106-4.png)

&nbsp; 따라서 멀티 모듈을 도입하게 되었고, 그렇게 행복할줄만 알았는데 새로운 고민이 생겼다.

![20240106-5.png](/assets/img/posts/20240106-5.png)

&nbsp; 바로 공통 모듈이다. Common 하니까 common 패키지가 생각났고, As-Is 프로젝트의 `Global` 패키지가 생각나서 일단 다 넣어보기로 했다. 그랬더니 Common 모듈은 그 어떤 모듈보다도 거대한 모듈이 되었다.

![20240106-6.png](/assets/img/posts/20240106-6.png)

&nbsp; 위 사진처럼 global 패키지에서 많은 부분을 Common 모듈에 넣었고, 특히 `Configuration` 관련 클래스를 모두 넣으면서 Common 모듈은 고도비만이 되어 버렸다.

&nbsp; 그래서 다시 고민해보았다. `모듈을 구성할 때 가장 중요한 것은 무엇일까?` 그렇게 내린 결론은 `제약을 두고 각 계층의 책임과 역할을 명확히 하자` 였다.

<br>

## 행동 (Action): 멀티모듈 설계와 구현

> ### 행동 1: 모듈 분리 기준

![20240106-7.png](/assets/img/posts/20240106-7.png)

&nbsp; 먼저 모듈 분리 기준을 크게 네 가지로 세워보았다. 애플리케이션 비즈니스를 가지고 있으면 `애플리케이션 모듈`, 애플리케이션 비즈니스는 모르지만 도메인 비즈니스를 가지고 있으면 `도메인 모듈`, 비즈니스를 가지고 있지는 않지만 시스템과의 연관성이 있으면 `내부 모듈`, 시스템과의 연관성은 없지만 외부 시스템과의 연관성이 있으면 `외부 모듈`로 분리하였다.

<br>

> ### 행동 2: 모듈 계층별 제약 및 구성

![20240106-8.png](/assets/img/posts/20240106-8.png)

**_공통 모듈_**

- 공통 코드를 다루는 계층
- 어떠한 의존 관계도 추가할 수 없음
- 순수 Java 코드로 이루어짐
- Type(Enum 포함), Util, 공통 Exception 및 공통 Response 포맷 클래스로 구성

![20240106-9.png](/assets/img/posts/20240106-9.png)

**_도메인 모듈_**

- DB와 밀접한 도메인을 다루는 계층
- 애플리케이션 비즈니스를 모름
- 하나의 도메인 모듈은 최대 하나의 Infrastructure에 대한 책임을 가짐
- Entity, Repository, Domain Service(Reader, Writer)로 구성

![20240106-10.png](/assets/img/posts/20240106-10.png)

**_내부 모듈_**

- 비즈니스를 모르지만 시스템과 연관성을 갖고 있는 계층
- Client 모듈
  - 시스템과 연관성을 가지며 외부 시스템과의 통신 담당
  - WebClient, 관련 DTO

![20240106-11.png](/assets/img/posts/20240106-11.png)

**_애플리케이션 모듈_**

- 독립적으로 실행 가능한 계층
- 시스템 비즈니스 로직을 가짐
- 다른 모듈들을 조합하여 비즈니스 구성
- Internal Api 모듈, Batch 모듈

![20240106-12.png](/assets/img/posts/20240106-12.png)

**_외부 모듈_**

- 외부 시스템과 연관성을 갖고 있는 계층
- FCM, S3가 포함됨
- Common 모듈만 의존 관계 추가 가능

<br>

> ### 행동 3: 의존 관계 개방•폐쇄하기

&nbsp; 라이브러리 종속성을 추가할 때도 개방과 폐쇄를 고려해야 한다. 그렇다면 `implementation`과 `api` 중 어떤 걸 써야 할까?

![20240106-13.png](/assets/img/posts/20240106-13.png)

&nbsp; 공식 문서에는 위와 같이 가능한 `api`가 아닌 `implementation`을 사용하라고 쓰여 있다. 그 이유를 알기 위해 child-module과 parent-module을 임의로 생성하여 테스트를 진행했다.

![20240106-14.png](/assets/img/posts/20240106-14.png)

&nbsp; 위와 같이 child-module에 한번은 implementation, 또 한번은 api를 활용하여 의존성을 추가해 보았다. 그 결과, implementation과 api 모두 `compileClassPath`, `testCompileClassPath`, `runtimeClassPath`, `testRuntimeClassPath`에 추가되고 있음을 확인할 수 있었다.

![20240106-15.png](/assets/img/posts/20240106-15.png)

&nbsp; 다음은 parent-module에 child 모듈 의존성을 추가해보았다. 

![20240106-16.png](/assets/img/posts/20240106-16.png)

&nbsp; 그 결과, api를 사용한 경우엔 동일하게 `compileClassPath`, `testCompileClassPath`, `runtimeClassPath`, `testRuntimeClassPath`에 추가되었지만, implementation을 사용한 경우엔 `runtimeClassPath`, `testRuntimeClassPath`에만 추가됨을 확인할 수 있었다. 따라서 implementation을 활용했을 때 다음과 같은 이점을 취할 수 있다.

- 불필요한 의존성 전파 방지
- compileClassPath 의존성 충돌 방지
- 하위 모듈에서 의존성을 변경해도 상위 모듈 재컴파일 X
- 컴파일 속도 향상

&nbsp; 물론 implementation만 잘 활용한다고 개방과 폐쇄에 대해 완벽히 설계했다고 할 수 없다. 라이브러리 종속성을 추가할 때는 최소한의 라이브러리인지 항상 생각하고 추가하도록 하자.

<br>

## 결과 (Result): 멀티모듈 도입이 가져온 영향과 결론

![multi-module](https://github.com/wooda-ege/lovebird-server/assets/56003992/e45b1ce3-fcd0-4aa5-98bd-1a6a0661b39d)

&nbsp; Lovebird 서버의 최종 멀티모듈 구조이다. Batch 서버 개발 진행중 및 External 서버 미분리 등 아직 동일하게 구현돼있지는 않지만, 최종적으로 나아갈 구조이다. (도메인 별 모듈 분리는 아직 도메인 자체를 분리할 필요는 없다고 판단하여 고려하지 않았다.)

> ### 멀티모듈 도입이 가져온 이점

![20240106-17.png](/assets/img/posts/20240106-17.png)

&nbsp; 멀티모듈을 도입하면서 우리는 재사용성과 확장성을 보장 받았고, 프로젝트 관리 및 유지보수가 수월해졌으며, 빌드 속도가 개선되고 배포가 수월해지는 이점을 취할 수 있었다.

> ### 결론: 사이드 프로젝트에 멀티 모듈을 도입하는 것이 맞을까 ?

&nbsp; 주관적인 의견으로는 `NO !` 이다. 멀티모듈은 아키텍처나 디자인 패턴이 아니다. 초기 설계 과정에서 많은 비용이 들고, 제대로 활용하지 못한다면 멀티모듈은 무용지물이 될 것이다. 따라서 MVP를 개발할 때가 아닌 리팩토링 과정, 또는 도메인에 대한 확실한 이해가 있을 때 멀티 모듈 도입에 대하여 고민해보자.

<br>

### Reference

- [우아한 Tech 블로그](https://techblog.woowahan.com/2637/)
- [@hudi](https://hudi.blog/why-use-multi-module/)
- [@cofls6581](https://cofls6581.tistory.com/274)
