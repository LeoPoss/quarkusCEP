package de.ur.bdd;

import io.quarkiverse.cucumber.CucumberOptions;
import io.quarkiverse.cucumber.CucumberQuarkusTest;

@CucumberOptions(features = "src/test/resources/de/ur/features", glue = "de.ur.bdd")
public class CucumberTestRunner extends CucumberQuarkusTest {
    // Entry point for Cucumber tests in Quarkus
}
