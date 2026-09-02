# Gauge Typed Parameters

Manual test bed. Put the caret inside a quoted parameter and press Ctrl+Space
(Cmd+Space on macOS) to see values derived from the Java parameter type.

## Enum parameter

* "LOGIN_BUTTON" elementine tiklanir

## Two enum parameters, no cross contamination

* "CHROME" ile "LOGIN_BUTTON" elementine tiklanir

## Three parameters - the caret decides which one is completed

* "CHROME" ile "3" kere "LOGIN_BUTTON" elementine tiklanir

## Boolean parameter

* "true" aktif edilir

## Numeric parameter

* "3" kere denenir

## String parameter - deliberately no typed completion

* "serbest metin" yazilir

## java.lang.Enum parameter - the project enum browser

Type Ctrl+Space inside the quotes to get enum CLASS names (PageItems, PageItems2,
HeaderItems), pick one, type a dot, and press Ctrl+Space again to get that class's
constants. Picking a constant replaces the whole "PageItems2.LO" text with it.

* "LOGIN_BUTTON" menusune git

## Invalid values - the inspection highlights these

* "LOGNI_BUTTON" elementine tiklanir
* "tru" aktif edilir
* "abc" kere denenir
