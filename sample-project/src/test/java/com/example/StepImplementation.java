package com.example;

import com.thoughtworks.gauge.Step;

public class StepImplementation {

    @Step("<element> elementine tiklanir")
    public void clickElement(Element element) {
    }

    @Step("<browser> ile <element> elementine tiklanir")
    public void clickElementInBrowser(Browser browser, Element element) {
    }

    @Step("<browser> ile <retryCount> kere <element> elementine tiklanir")
    public void clickElementWithRetries(Browser browser, int retryCount, Element element) {
    }

    @Step("<enabled> aktif edilir")
    public void enable(boolean enabled) {
    }

    @Step("<count> kere denenir")
    public void retry(int count) {
    }

    @Step("<text> yazilir")
    public void type(String text) {
    }

    /**
     * Declaring the parameter as the raw java.lang.Enum is an intentional signal: completion
     * first offers the project's enum CLASS names, and after typing a dot the constants of that
     * class. Only the constant is written to the .spec file, and this method resolves which
     * enum it belongs to at run time.
     */
    @Step("<item> menusune git")
    public void goToMenu(Enum item) {
    }
}
