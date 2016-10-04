package org.aksw.twig.automaton.data;

import org.junit.Assert;
import org.junit.Test;

public class MessageCounterTest {

    @Test
    public void test() {
        MessageCounter counter = new MessageCounter();
        String userName1 = "a";
        String userName2 = "b";
        counter.setUserMessages(userName1, 2);
        counter.setUserMessages(userName2, 2);

        counter.getUserMessageCountMap().forEach(entry -> {
            String key = entry.getKey();
            if (key.equals(userName1)) {
                Assert.assertEquals(new Integer(2), entry.getValue());
            } else if (key.equals(userName2)) {
                Assert.assertEquals(new Integer(2), entry.getValue());
            } else {
                Assert.fail();
            }
        });

        Assert.assertEquals(new Integer(2), counter.getMessageCounts().get(1));
    }
}