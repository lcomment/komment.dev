---
title: [(Spring Boot) S3 Presigned URL을 활용하여 요청 크기를 줄이자]
date: 2023-11-17 15:33:44 +09:00
categories: [Spring, 트러블 슈팅]
tags: [스프링, aws, s3, presigned url, Lovebird]

--- 

## 문제 상황

&nbsp; 파일 업로드 작업은 프로젝트 내에서 가장 많이 구현하는 기능 중 하나다. 이 기능을 구현할 때 많은 개발자들이 `Amazon S3`(; Simple Storage Service)를 사용하고, Lovebird 프로젝트에서도 S3를 활용하여 간단히 구현했다.

&nbsp; 하지만 `다이어리`를 작성하면서 다수의 이미지, 큰 용량의 동영상을 API에 담아 서버로 보낸다면 API 요청이 무거워져 부하가 발생한다. 그럼에도 서버로 보내는 이유는 보안 때문인데, API 요청을 가볍게 만들고, 보안적인 이슈까지 잡을 수 있는 방법이 `Presigned Url`을 활용하는 것임을 알게 되었고, 적용하기로 결정했다.

## Presigned URL

&nbsp; Presigned URL은 S3의 Bucket Policy나 ACL 등의 권한설정과 `관계없이` 특정 `유효기간`동안 S3에 접근 가능하게 하는 URL이다. 보안이나 성능을 높이기 위해 많이 사용하는데, Lovebird에서는 업로드 기능에만 사용하기로 했다.

```text
https://lovebird-test.s3.ap-northeast-2.amazonaws.com/users/1/profile/image.png
?X-Amz-Algorithm=AWS4-HMAC-SHA256
&X-Amz-Date=20231117T070602Z
&X-Amz-SignedHeaders=host
&X-Amz-Expires=180
&X-Amz-Credential=%2F20231117%2Fap-northeast-2%2Fs3%2Faws4_request
&X-Amz-Signature=a492f3e8a126ff74e8a074de6e46bbc25ce19874427e0a887127dba1e4bf5756
```

&nbsp; 위는 Mock S3로 발급 받은 Presigned URL이다. Query String을 살펴보자.

> X-Amz-Algorithm
- 서명 버전과 알고리즘 식별 및 서명 계산

> X-Amz-Credential
- Access-Key ID와 범위 정보(요청 날짜, 사용하는 리전, 서비스 명)

> X-Amz-Date
- ISO 8601 형식의 날짜

> X-Amz-Expires
- 유효 시간 주기 (초 단위)

> X-Amz-SignedHeaders
- 서명을 계산하기 위해 사용되는 헤더 목록

> X-Amz-Signature
- 요청 인증하기 위한 서명

## 업로드용 Presigned URL 발급 로직 구현

> ### 의존성 추가

```java
implementation 'com.amazonaws:aws-java-sdk-s3:1.12.566'
testImplementation 'io.findify:s3mock_2.13:0.2.6'
```

&nbsp; aws 관련 의존성과 Mock S3를 위한 의존성을 추가한다.

> ### AwsS3Config.java

```java
@Configuration
public class AwsS3Config {

    @Value("${cloud.aws.credentials.access-key}")
    private String iamAccessKey;
    @Value("${cloud.aws.credentials.secret-key}")
    private String iamSecretKey;
    @Value("${cloud.aws.region.static}")
    private String region;
    @Value("${cloud.aws.s3-bucket}")
    private String bucketName;

    @Bean
    public AmazonS3Client amazonS3Client() {
        BasicAWSCredentials basicAWSCredentials = new BasicAWSCredentials(iamAccessKey, iamSecretKey);
        return (AmazonS3Client) AmazonS3ClientBuilder.standard()
                                                     .withRegion(region)
                                                     .withCredentials(new AWSStaticCredentialsProvider(
                                                             basicAWSCredentials))
                                                     .build();
    }

    public String getBucketName() {
        return bucketName;
    }
}
```

&nbsp; yml 파일에 설정값을 활용하여 `AmazonS3Client`를 Bean에 등록해준다. 또 `bucket name`이 필요하기 때문에 getter를 하나 구현해준다. (다른 변수들은 필요하지 않기 때문에 Lombok의 `@Getter`를 사용하지 않았다.)

> ### PresignedUrlProvider.java

```java
@Component
@RequiredArgsConstructor
public class PresignedUrlProvider {

	private final AwsS3Config awsS3Config;

	public String getUploadPresignedUrl(String domain, String memberId, String filename) {
		String path = getPath(memberId, domain, filename);

		return getPresignedUrl(path, PUT);
	}

	private String getPresignedUrl(String path, HttpMethod method) {
		return awsS3Config.amazonS3Client().generatePresignedUrl(getGeneratePresignedUrlRequest(path, method, getExpiration())).toString();
	}

	private GeneratePresignedUrlRequest getGeneratePresignedUrlRequest(String path, HttpMethod method, Date expiration) {
		return new GeneratePresignedUrlRequest(awsS3Config.getBucketName(), path, method).withExpiration(expiration);
	}

	private Date getExpiration() {
		Date date = new Date();
		date.setTime(date.getTime() + 180_000);

		return date;
	}

	private String getPath(String memberId, String domain, String filename) {
		return "users/%s/%s/%s".formatted(memberId, domain, filename);
	}
}
```

&nbsp; `getGeneratePresignedUrlRequest()` 메서드를 통해 PresignedUrl 발급을 위한 요청 객체를 만들고 `getPresignedUrl()`를 통해 발급 받았다. public 메서드인 `getUploadPresignedUrl()` 메서드만 외부에 노출해 Presigned Url 발급 역할을 하고 있다.

## 테스트

> ### S3MockConfig.java

```java
@TestConfiguration
public class S3MockConfig {

    @Value("${cloud.aws.region.static}")
    String region;

    @Value("${cloud.aws.s3-bucket}")
    String s3Bucket;

    @Bean
    public S3Mock s3Mock() {
        return new S3Mock.Builder()
                .withPort(8001)
                .withInMemoryBackend()
                .build();
    }

    @Primary
    @Bean
    public AmazonS3 amazonS3(S3Mock s3Mock) {
        s3Mock.start();
        AwsClientBuilder.EndpointConfiguration endPoint = new AwsClientBuilder.EndpointConfiguration(
                "http://localhost:8001", region);

        AmazonS3 amazonS3Client = AmazonS3ClientBuilder
                .standard()
                .withPathStyleAccessEnabled(true)
                .withEndpointConfiguration(endPoint)
                .withCredentials(new AWSStaticCredentialsProvider(new AnonymousAWSCredentials()))
                .build();
        amazonS3Client.createBucket(s3Bucket);

        return amazonS3Client;
    }
}
```

&nbsp; Mock S3를 활용하기 위해 설정을 먼저 해준다. S3Mock을 사용하는 Bean이 실제 Amazon S3가 아닌 모킹한 Amazon S3로 지정하기 위해 `@Primary` 어노테이션을 붙여주었다.

> ### PresignedUrlProviderTest.java

```java
@SpringBootTest
@Import(S3MockConfig.class)
public class PresignedUrlProviderTest {

	@Autowired
	private PresignedUrlProvider presignedUrlProvider;

	@Autowired
	private S3Mock s3Mock;

	@AfterEach
	void shutdownMockS3() {
		s3Mock.stop();
	}

	@Test
	@DisplayName("Presigned Url 발급 테스트")
	void generatePresignedUrl() {
		// given
		String domain = "profile";
		String memberId = "1";
		String filename = "image.png";

		// when
		String presignedUrl = presignedUrlProvider.getUploadPresignedUrl(domain, memberId, filename);
		
		// then
		assertThat(presignedUrl).startsWith(
			"https://lovebird-test.s3.ap-northeast-2.amazonaws.com/users/%s/%s/%s?".formatted(memberId, domain, filename));
	}
}
```

&nbsp; 테스트를 돌려보면 다음과 같이 PASS된 것을 확인할 수 있다. 추가적으로 **하나의 Presigned URL은 하나의 파일에만 사용**되니 여러 파일을 업로드할 때는 여러 Presigned URL을 발급 받아야 한다. 

![20231117-1](/assets/img/posts/20231117-1.png)