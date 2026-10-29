package com.juge.lklpay

import org.junit.jupiter.api.Test
import org.springframework.boot.test.context.SpringBootTest

// 必须与其余 @SpringBootTest 一样显式覆盖数据源：否则会继承 application.yml 的
// file 模式 H2（./data/paydb + ddl-auto: update），跑测试会打开甚至改写真实库文件。
@SpringBootTest(properties = [
	"spring.datasource.url=jdbc:h2:mem:context-load;DB_CLOSE_DELAY=-1",
	"spring.jpa.hibernate.ddl-auto=create-drop",
])
class LklPayServerApplicationTests {

	@Test
	fun contextLoads() {
	}

}
