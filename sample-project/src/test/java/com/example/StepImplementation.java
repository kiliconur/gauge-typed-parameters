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
     * A plain String parameter. The value stays free text - anything is legal here - but
     * completion offers the project enum browser as assistance: enum CLASS names first, then
     * that class's constants after a dot. Only the constant is written to the .spec file, and
     * this method decides at run time which enum it belongs to.
     */
    @Step("<item> menusune git")
    public void goToMenu(String item) {
    }
}
