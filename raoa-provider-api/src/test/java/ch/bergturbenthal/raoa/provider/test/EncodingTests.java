package ch.bergturbenthal.raoa.provider.test;

import org.junit.Assert;
import org.junit.Test;

import ch.bergturbenthal.raoa.provider.SortOrder;
import ch.bergturbenthal.raoa.provider.SortOrderEntry.Order;
import ch.bergturbenthal.raoa.provider.criterium.Boolean;
import ch.bergturbenthal.raoa.provider.criterium.Compare;
import ch.bergturbenthal.raoa.provider.criterium.Constant;
import ch.bergturbenthal.raoa.provider.criterium.Criterium;
import ch.bergturbenthal.raoa.provider.criterium.Field;

public class EncodingTests {
	@Test
	public void testCriterium() {
		final Criterium crit = Boolean.and(Compare.eq(new Field("col3"), new Field("col4")), Compare.eq(new Field("col1"), new Constant("v1")));
		final String criteriumString = crit.makeString();
		// System.out.println(crit);
		// System.out.println(criteriumString);
		final Criterium copy = Criterium.decodeString(criteriumString);
		Assert.assertEquals(crit, copy);
	}

	@Test
	public void testEncodeSortOrder() {
		final SortOrder original = new SortOrder();
		original.addOrder("col1", Order.ASC);
		original.addOrder("col2", Order.DESC);
		original.addOrder("col4", Order.ASC, false);
		original.addOrder("col5", Order.DESC, true);
		final String orderString = original.makeString();
		final SortOrder copy = SortOrder.decodeString(orderString);
		Assert.assertEquals(copy, original);
	}
}
