mvn-remove-unused-dependencies
==============================

Analyse which dependency is not needed and
update pom.xml with removed dependency.

Changes scopy to test in case of test-only dependency.

Usage:
mvn de.quaddyservices.mvn.plugin.unused:mvn-remove-unused-dependencies:remove

(for single parents):
mvn de.quaddyservices.mvn.plugin.unused:mvn-remove-unused-dependencies:remove -Dremove.includeParent=true

To Debug use
-Dremove.debug2ndMaven=true