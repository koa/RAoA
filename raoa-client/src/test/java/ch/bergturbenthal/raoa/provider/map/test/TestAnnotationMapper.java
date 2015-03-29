package ch.bergturbenthal.raoa.provider.map.test;

import java.util.Map;

import org.junit.Assert;
import org.junit.Test;

import ch.bergturbenthal.raoa.provider.map.CursorField;
import ch.bergturbenthal.raoa.provider.map.FieldReader;
import ch.bergturbenthal.raoa.provider.map.MapperUtil;

public class TestAnnotationMapper {

	public static class TestBean {
		private int		 intValue;
		private String	stringValue;

		@CursorField("intVal")
		public int getIntValue() {
			return intValue;
		}

		@CursorField("strVal")
		public String getStringValue() {
			return stringValue;
		}

		public void setIntValue(final int intValue) {
			this.intValue = intValue;
		}

		public void setStringValue(final String stringValue) {
			this.stringValue = stringValue;
		}
	}

	@Test
	public void testAnnotationMapperReader() {
		final Map<String, FieldReader<TestBean>> testBeanReaders = MapperUtil.makeAnnotatedFieldReaders(TestBean.class);
		final TestBean testBean = new TestBean();
		testBean.setStringValue("string dummy");
		testBean.setIntValue(42);
		Assert.assertEquals("string dummy", testBeanReaders.get("strVal").getString(testBean));
		Assert.assertEquals(42, testBeanReaders.get("intVal").getNumber(testBean).intValue());
	}
}
