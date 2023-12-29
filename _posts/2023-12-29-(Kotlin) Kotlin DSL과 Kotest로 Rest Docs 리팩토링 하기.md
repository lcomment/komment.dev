---
title: [(Kotlin) Kotlin DSL과 Kotest로 Rest Docs 리팩토링 하기]
date: 2023-12-29 12:00:00 +09:00
categories: [Spring, Rest Docs]
tags: [kotlin, kotest, Lovebird, 스프링, rest docs]
---

&nbsp; 2023년 11월 23일, Lovebird의 서버를 전체 리팩토링 하기로 결정했다. `as-is`에는 Java를 활용하여 개발했지만, `to-be`에서는 Kotlin을 사용하기로 결정하면서 같으면서도 정말 많은 부분이 달라졌고, 이번 포스팅에선 그 중 테스트 코드 관련된 이야기를 해보려 한다. 

```text
✅ 참고
- 전체적인 리팩토링 관련 내용은 주제를 따로 잡고 다루겠습니다.
- Kotest와 mockK 관련 이론에 대해서는 자세히 다루지 않았습니다.
```

## JUnit에서 Kotest로

&nbsp; 혼자 코프링(Kotlin + Srping)을 학습한지 2개월이 되면서 여러가지 일로 깊이 있게 익히진 못했지만 nullable, sealed class 등 Kotlin의 장점에 대해 감탄하고 있었지만, 아무래도 Kotlin보다 Java에 익숙해서 `JUnit`, `AssertJ`, `Mockito`를 활용하여 테스트 코드를 작성했다. 그러던 중 비즈니스 로직에서는 Kotlin DSL을 활용하고 있는데 테스트 코드에서는 활용하지 못하고 있다는 점에서 괴리를 느끼게 되었다. 아래 코드를 보면 `Assertion`과 `Mocking` 과정에서 Kotlin DSL을 활용하고 있지 못함을 확인할 수 있다.

```kotlin
. . .
    @Test
    fun `다이어리 이미지 업로드용 Presigned Url을 얻는다.`() {
        // given
        val param = DiaryUploadPresignedUrlParam(userId, diaryId, filenames)

        given(presignedUrlProvider.getUploadPresignedUrl(domain.lower(), param.userId, newFilename1))
            .willReturn(presignedUrl1)
        given(presignedUrlProvider.getUploadPresignedUrl(domain.lower(), param.userId, newFilename2))
            .willReturn(presignedUrl2)

        // when
        val response = presignedUrlService.getDiaryPresignedUrls(param)

        // then
        assertThat(response.presignedUrls[0].presignedUrl).isEqualTo(presignedUrl1)
        assertThat(response.presignedUrls[0].filename).isEqualTo(newFilename1)
        assertThat(response.presignedUrls[1].presignedUrl).isEqualTo(presignedUrl2)
        assertThat(response.presignedUrls[1].presignedUrl).isEqualTo(newFilename2)
        assertThat(response.totalCount).isEqualTo(2)
    }
. . .
```

<br>

&nbsp; 이런 어색함을 해결하기 위해 Lovebird 서버팀에서 선택한 테스트 프레임워크는 `Kotest`다. Kotest는 Kotlin 진영에서 가장 많이 사용하는 테스트 프레임워크로, Kotlin DSL 스타일의 Assertion과 다양한 테스트 레이아웃을 제공한다. (JUnit과도 호환이 되지만) 추가로 `mockK`까지 사용하면서 최대한 `Kotlin스러운` 테스트 코드를 작성하기로 결정하였다. mockK가 내부적으로 `ByteBuddy library`를 사용하여 속도가 느리다는 단점이 있지만([관련 레퍼런스](https://stackoverflow.com/questions/62208145/why-is-mocking-so-slow-to-start-in-kotlin)), 테스트 작성에 초점을 더 맞추기로 하였고, 다른 방법을 통해 점차 테스트 속도를 향상시켜 나가기로 했다. 아래는 Kotest를 활용한 테스트 코드다.

```kotlin
. . .
    val presignedUrlProvider: PresignedUrlProvider = mockk<PresignedUrlProvider>(relaxed = true)
	val fileNameProvider = FilenameProvider()
	val presignedUrlService = PresignedUrlService(presignedUrlProvider, fileNameProvider)
. . .
    describe("getDiaryPresignedUrls()") {
		. . .
		context("정상적인 Parameter가 주어졌을 때") {
			val param = DiaryUploadPresignedUrlParam(userId, diaryId, filenames)

			every { 
                presignedUrlProvider.getUploadPresignedUrl(domain.lower(), param.userId, newFilename1) 
            } returns presignedUrl1
			every { 
                presignedUrlProvider.getUploadPresignedUrl(domain.lower(), param.userId, newFilename2) 
            } returns presignedUrl2

			it("다이어리 사진 업로드용 presigned url을 반환한다.") {
				val response: PresignedUrlListResponse = presignedUrlService.getDiaryPresignedUrls(param)

				response.presignedUrls[0].presignedUrl shouldBe presignedUrl1
				response.presignedUrls[0].filename shouldBe newFilename1
				response.presignedUrls[1].presignedUrl shouldBe presignedUrl2
				response.presignedUrls[1].filename shouldBe newFilename2
				response.totalCount shouldBe 2
			}
		}
	}
. . .
```

<br>

&nbsp; JUnit을 활용했을 때보다 Kotlin스러워진 것을 확인할 수 있다. 추가적으로 GWT(Given-When-Then) 패턴에서 DCI(Describe-Context-It) 패턴으로 변경하였는데, 아래와 같은 장점에 대해 크게 공감했고, Kotest에서 `DescribeSpec` 추상 클래스를 통해 DCI 패턴의 스타일을 지원해주고 있어서 변경하게 되었다.

- `계층 구조`의 테스트 코드
- 테스트 추가 작성 및 읽을 때 → `스코프 범위`만 신경 쓰면 됨
- 테스트 결과가 트리 구조로 출력 → `가독성` 향상

## Rest Docs에 Kotlin DSL 뿌리기

&nbsp; Kotest 덕분에 테스트 코드가 이뻐졌다(?). 하지만 아직도 말썽인 것이 있었다. 바로 `Rest Docs`다. 아래는 DescribeSpec 스타일로 작성된 Rest Docs 코드 중 `It` 부분의 코드다.

```Kotlin
. . .
    it("200 OK") {
	    every { presignedUrlService.getDiaryPresignedUrls(requestBody.toParam(1L)) } returns response

		mockMvc
			.perform(request)
			.andExpect(status().isOk)
			.andExpectAll(
				jsonPath("$.code").value(ReturnCode.SUCCESS.code),
				jsonPath("$.message").value(ReturnCode.SUCCESS.message),
				jsonPath("$.data.presignedUrls[0].presignedUrl").value(response.presignedUrls[0].presignedUrl),
				jsonPath("$.data.presignedUrls[0].filename").value(response.presignedUrls[0].filename),
				jsonPath("$.data.presignedUrls[1].presignedUrl").value(response.presignedUrls[1].presignedUrl),
				jsonPath("$.data.presignedUrls[1].filename").value(response.presignedUrls[1].filename),
				jsonPath("$.data.totalCount").value(response.totalCount)
            )
			.andDo(
				document(
					"200-diary-presigned-url",
					preprocessRequest(prettyPrint()),
					preprocessResponse(prettyPrint()),
					requestHeaders(
						headerWithName("Authorization").description("액세스 토큰")
					),
					requestFields(
						fieldWithPath("filenames").type(JsonFieldType.ARRAY).description("파일 이름 리스트"),
						fieldWithPath("diaryId").type(JsonFieldType.NUMBER).description("다이어리 ID"),
					),
					responseFields(
						fieldWithPath("code").type(JsonFieldType.STRING).description("응답 코드"),
						fieldWithPath("message").type(JsonFieldType.STRING).description("응답 메세지"),
						fieldWithPath("data").type(JsonFieldType.OBJECT).description("응답 데이터"),
						fieldWithPath("data.presignedUrls").type(JsonFieldType.ARRAY).description("Presigned Url 리스트"),
						fieldWithPath("data.presignedUrls[].presignedUrl").type(JsonFieldType.STRING).description("Presigned Url"),
						fieldWithPath("data.presignedUrls[].filename").type(JsonFieldType.STRING).description("파일 이름"),
						fieldWithPath("data.totalCount").type(JsonFieldType.NUMBER).description("총 개수"),
					)
				)
		    )
	}
```

&nbsp; Response에 대한 검증 부분은 그렇다 치더라도 Rest Docs 작성법은 다음과 같은 문제점이 있었다.

- 반복되는 호출이 많다.
- 가독성이 떨어진다.

&nbsp; `andDo(document())`, `preprocessRequest(prettyPrint())`, `preprocessResponse(prettyPrint())`는 반복해서 호출되고 있고, 체이닝 메서드들이 어지럽게 나열돼 있다. 심지어 포맷팅을 적용하면 가끔씩 이상하게 개행되어 안 그래도 구린 가독성을 더 안 좋게 만든다. 이러한 문제점들을 `infix function`(중위함수)와 문자열 `extension functions`(확장함수)을 통해 개선 해보고자 한다.

> ### andDo(document()) → andDocument()

&nbsp; 먼저 확장함수를 활용하여 andDocument()를 구현하여 andDo(document())의 반복 호출 문제를 해결해주었다.

```kotlin
fun ResultActions.andDocument(
	identifier: String,
	vararg snippets: Snippet
): ResultActions {
	return andDo(document(identifier, *snippets))
}
```

<br>

> ### Descriptor의 체이닝 메서드 개선하기

```kotlin
// 1
fieldWithPath("code").type(JsonFieldType.STRING).description("응답 코드")

// 2
"code" type STRING means "응답 코드"
```

&nbsp; 위의 두 코드는 같은 코드다. '어떤 코드가 좋아?'라고 묻는다면 대다수가 당연히 2번을 선택할 것이다. 다음 코드를 살펴보자.

```kotlin
// RestDocsField.kt

class RestDocsField(
	val descriptor: FieldDescriptor
) {
    . . .
	infix fun means(value: String): RestDocsField {
		descriptor.description(value)
		return this
	}

	infix fun attributes(block: RestDocsField.() -> Unit): RestDocsField {
		block()
		return this
	}
	. . .
}

infix fun String.type(
	docsFieldType: DocsFieldType
): RestDocsField {
	return createField(this, docsFieldType.type)
}

private fun createField(
	value: String,
	type: JsonFieldType,
	optional: Boolean = true
): RestDocsField {
	val descriptor = PayloadDocumentation.fieldWithPath(value)
		.type(type)
		.description("")

	if (optional) descriptor.optional()

	return RestDocsField(descriptor)
}
. . .
```

&nbsp; type() 메서드를 infix notation으로 선언해주고, `String`을 receiver로, `DocsFieldType`을 parameter로 받고 있다. DocsFieldType은 `JsonFieldType`을 담고 있는 sealed class이고, createField() 메서드를 통해 Rest Docs를 만드는 동작을 수행한 후, `FieldDescriptor`를 다루기 위해 정의한 RestDocsField를 리턴한다. DocsFieldType은 아래와 같은 형식으로 구현한다.

```kotlin
sealed class DocsFieldType(
	val type: JsonFieldType
)

object ARRAY : DocsFieldType(JsonFieldType.ARRAY)
. . .
```

<br>

&nbsp; Field와 마찬가지로 Header나 Param에 대해서도 구성할 수 있다.

```kotlin
// RestDocsHeader.kt
class RestDocsHeader(
	val descriptor: HeaderDescriptor
)

infix fun String.headerMeans(
	description: String
): RestDocsHeader {
	return createField(this, description)
}
. . .

// RestDocsParam.kt
class RestDocsParam(
	val descriptor: ParameterDescriptor
)

infix fun String.pathMeans(
	description: String
): RestDocsParam {
	return createField(this, description)
}
. . .
```

<br>

&nbsp; 이렇게 Kotlin DSL을 한 스푼 넣으면 아래와 같이 멀끔해진 Rest Docs를 만날 수 있다.

```kotlin
. . .
    it("200 OK") {
		every { presignedUrlService.getDiaryPresignedUrls(requestBody.toParam(1L)) } returns response

		mockMvc
			.perform(request)
			.andExpect(status().isOk)
			.andExpectData(
				jsonPath("$.code") shouldBe ReturnCode.SUCCESS.code,
				jsonPath("$.message") shouldBe ReturnCode.SUCCESS.message,
				jsonPath("$.data.presignedUrls[0].presignedUrl") shouldBe response.presignedUrls[0].presignedUrl,
				jsonPath("$.data.presignedUrls[0].filename") shouldBe response.presignedUrls[0].filename,
				jsonPath("$.data.presignedUrls[1].presignedUrl") shouldBe response.presignedUrls[1].presignedUrl,
				jsonPath("$.data.presignedUrls[1].filename") shouldBe response.presignedUrls[1].filename,
				jsonPath("$.data.totalCount") shouldBe response.totalCount
			)
			.andDocument(
				"200-diary-presigned-url",
				requestHeaders(
					"Authorization" headerMeans "액세스 토큰"
				),
				requestBody(
					"filenames" type ARRAY means "파일 이름 리스트",
					"diaryId" type NUMBER means "다이어리 ID"
				),
				responseBody(
					"code" type STRING means "응답 코드",
					"message" type STRING means "응답 메시지",
					"data" type OBJECT means "응답 데이터",
					"data.presignedUrls" type ARRAY means "Presigned Url 리스트",
					"data.presignedUrls[].presignedUrl" type STRING means "Presigned Url",
					"data.presignedUrls[].filename" type STRING means "파일 이름",
					"data.totalCount" type NUMBER means "총 개수"
				)
			)
	}
. . .
```

<br>

→ 포스팅에 활용된 코드 및 Lovebird 서버 코드는 [Lovebird 깃헙](https://github.com/wooda-ege/lovebird-server)에 올라와있습니다!

<br>

> ### Reference

- [Kotest Docs](https://kotest.io/)
- [Woowahan](https://techblog.woowahan.com/5825/)
- [Toss](https://toss.tech/article/21003)
- [tistory-jaehhh](https://jaehhh.tistory.com/118)
- [tistory-sang5c](https://sang5c.tistory.com/60)
