package com.jminjie.noober;

import org.junit.Before;
import org.junit.Test;

import static org.junit.Assert.*;

/**
 * Example local unit test, which will execute on the development machine (host).
 *
 * @see <a href="http://d.android.com/tools/testing">Testing documentation</a>
 */
public class RequesterTest {
    private Requester mRequester;

    @Before
    public void onCreate() {
        mRequester = Requester.getInstance();
    }

    @Test
    public void singletonIsEnforced() throws Exception {
        assertEquals(Requester.getInstance(), Requester.getInstance());
    }
}