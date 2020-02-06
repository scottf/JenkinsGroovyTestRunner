/*
 * Copyright (C) 2018-2019 Arondight, Inc. and Scott Fauerbach
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

class TestCase {
    String name
    String classname
    long time
    String errorMessage
    String failureMessage
    String efType
}

class TestSuite {
    String name
    long time
    int failures
    List testCases = []
}

def newTestCase(name, classname) {
    new TestCase(name: name, classname: classname)
}

def newTestSuite(name) {
    new TestSuite(name: name)
}

def hr() {
    '----------------------------------------------------------------------------------------------------'
}

def timeStr(time) {
    if (time < 1) {
        "0.001"
    }
    else {
        "${time / 1000.0}"
    }
}

def partString(TestCase tc, boolean xml = true) {
    def timeStr = timeStr(tc.time)

    if (xml) {
        def error = tc.errorMessage == null ? "" : ">\n        <error message=\"${tc.errorMessage}\" type=\"${tc.efType}\"></error>\n"
        def failure = tc.failureMessage == null ? "" : ">\n        <failure message=\"${tc.failureMessage}\" type=\"${tc.efType}\"></failure>\n"
        def end = "$error$failure".length() == 0 ? "/>" : "    </testcase>"
        "<testcase name=\"${tc.name}\" classname=\"${tc.classname}\" time=\"${timeStr}\"${error}${failure}${end}"
    }
    else {
        def error = tc.errorMessage == null ? "" : " error=\"${tc.errorMessage}\", \"${tc.efType}\""
        def failure = tc.failureMessage == null ? "" : " failure=\"${tc.failureMessage}\", \"${tc.efType}\""
        "${tc.name} time=\"${timeStr}\"${error}${failure}"
    }
}

def partString(TestSuite suite, boolean xml = true) {
    long time = 0
    int errors = 0
    int failures = 0
    suite.testCases.each {
        time += it.time
        if (it.errorMessage != null) {
            errors++
        }
        if (it.failureMessage != null) {
            failures++
        }
    }

    def timeStr = timeStr(time)

    if (xml) {
        "<testsuite xmlns:xsi=\"http://www.w3.org/2001/XMLSchema-instance\" xsi:noNamespaceSchemaLocation=\"https://maven.apache.org/surefire/maven-surefire-plugin/xsd/surefire-test-report.xsd\" name=\"${suite.name}\" time=\"${timeStr}\" tests=\"${suite.testCases.size()}\" errors=\"${errors}\" skipped=\"0\" failures=\"${failures}\">"
    }
    else {
        "name=\"${suite.name}\" time=\"${timeStr}\" tests=\"${suite.testCases.size()}\" errors=\"${errors}\" skipped=\"0\" failures=\"${failures}\""
    }
}

def fullString(TestSuite suite, boolean xml = false) {
    def text = ""
    text = text + "${partString(suite, xml)}\n"
    suite.testCases.each {
        text = text + "    ${partString(it, xml)}\n"
    }
    if (xml) {
        "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n${text}</testsuite>\n"
    }
    else {
        "${text}"
    }
}

def runTests(unitTestsDirectory){
    // track the test suites
    def testSuiteList = []

    // get a list of files fron the unit test directory
    sh "ls -1 ${unitTestsDirectory} > unit-tests"
    def filenames = readFile('unit-tests').split("\\r?\\n");

    // for each file that ends with Test.groovy, it should have a method called getTests
    // that returns a map of test names to closures
    filenames.each { filename ->
        if (filename.endsWith("Test.groovy")) {
            // calculate suitename from filename
            def suitename = filename.substring(0, filename.length() - 11)

            // begin test suite
            println "\n${hr()}\nTESTING SUITE $suitename\n${hr()}\n"
            TestSuite suite = newTestSuite("var.${suitename}")
            testSuiteList.add(suite)

            // load the script and get the tests
            def script = load("${unitTestsDirectory}/${filename}")
            def testsMap = script.getTests()

            // each test in the map
            testsMap.each { testName, testClosure ->
                TestCase tc = newTestCase(testName, "var.${suite.name}")
                suite.testCases.add(tc)
                long start = new Date().getTime()
                try {
                    testClosure()
                }
                catch (Exception e) {
                    tc.errorMessage = e.getMessage()
                    tc.efType = e.getClass().getSimpleName()
                }
                catch (AssertionError ae) {
                    tc.failureMessage = ""
                    def parts = ae.toString().split("\n")
                    parts.each {
                        def t = it.trim()
                        if (t.length() > 0) {
                            tc.failureMessage = ("${tc.failureMessage} $t").trim()
                        }
                    }
                    tc.efType = ae.getClass().getSimpleName()
                }
                tc.time = new Date().getTime() - start
            }

            // end test suite
            println "\n${hr()}\nSUITE RESULTS $suitename\n${hr()}\n${fullString(suite, false)}${hr()}\n"
        }
    }

    // clean the results directory then write the results
    def allResults = "${hr()}\nALL SUITES TEST RESULTS\n${hr()}"
    sh 'rm -rf test-results; mkdir test-results'
    testSuiteList.each { suite ->
        allResults = "${allResults}\n${fullString(suite, false)}${hr()}"
        def text = fullString(suite, true)
        writeFile file: "test-results/TEST-${suite.name}.xml", text: text
    }

    // junit and archive
    junit testResults: "test-results/*.xml"
    archiveArtifacts artifacts: "test-results/*"

    println "${allResults}\n"
}

return this
