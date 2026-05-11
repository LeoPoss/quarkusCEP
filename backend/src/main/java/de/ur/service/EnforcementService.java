package de.ur.service;

import de.ur.dao.EnforcementRule;
import de.ur.dao.GenericEvent;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import lombok.extern.slf4j.Slf4j;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

@ApplicationScoped
@Slf4j
public class EnforcementService {

    @Inject
    EsperService esperService;

    @Inject
    ConstraintService constraintService;

    private final Map<String, EnforcementRule> rules = new ConcurrentHashMap<>();
    private final Map<String, String> deploymentIds = new ConcurrentHashMap<>();

    public EnforcementRule createRule(String name, String signalType,
                                       String conditionParam, String conditionOperator, String conditionValue,
                                       long durationSeconds, String actionEventType) {
        if (rules.containsKey(name)) {
            throw new IllegalArgumentException("Enforcement rule '" + name + "' already exists");
        }

        EnforcementRule rule = new EnforcementRule(name, signalType, conditionParam,
                conditionOperator, conditionValue, durationSeconds, actionEventType, true, null);
        rules.put(name, rule);
        deployRule(rule);
        log.info("Enforcement rule '{}' created: if {} {} {} for {}s → {}",
                name, conditionParam, conditionOperator, conditionValue, durationSeconds, actionEventType);
        return rule;
    }

    private void deployRule(EnforcementRule rule) {
        String antiOp = invertOperator(rule.getConditionOperator());
        String epl = String.format(
                "@name('enforcement-%s') " +
                "SELECT * FROM PATTERN [" +
                "  every a=GenericEvent(eventType='%s' AND cast(payload('%s'), double) %s %s)" +
                "  -> (timer:interval(%d sec) AND NOT " +
                "      b=GenericEvent(eventType='%s' AND cast(payload('%s'), double) %s %s))" +
                "]",
                rule.getName(),
                rule.getSignalType(), rule.getConditionParam(),
                rule.getConditionOperator(), rule.getConditionValue(),
                rule.getDurationSeconds(),
                rule.getSignalType(), rule.getConditionParam(),
                antiOp, rule.getConditionValue()
        );

        var statement = esperService.deployStatements("enforcement-" + rule.getName(), epl);
        String depId = statement.getDeploymentId();
        deploymentIds.put(rule.getName(), depId);

        esperService.addListener(depId, statement.getName(), (newEvents, oldEvents, s, r) -> {
            if (newEvents != null && newEvents.length > 0) {
                fireRule(rule);
            }
        });
    }

    private void fireRule(EnforcementRule rule) {
        log.info("Enforcement rule '{}' fired! Injecting: {}",
                rule.getName(), rule.getActionEventType());

        Map<String, String> payload = new HashMap<>();
        payload.put("source", "enforcement");
        payload.put("rule", rule.getName());

        GenericEvent event = new GenericEvent(
                UUID.randomUUID().toString(), rule.getActionEventType(),
                System.currentTimeMillis(), payload
        );

        if (constraintService.getKnownEvents().contains(rule.getActionEventType())) {
            constraintService.addToTrace(rule.getActionEventType(), payload, event.getTimestamp());
        } else {
            constraintService.getKnownEvents().add(rule.getActionEventType());
            constraintService.addToTrace(rule.getActionEventType(), payload, event.getTimestamp());
        }

        esperService.sendEvent(event);
        rule.setLastFiredAt(System.currentTimeMillis());
    }

    public void removeRule(String name) {
        EnforcementRule rule = rules.remove(name);
        if (rule != null) {
            String depId = deploymentIds.remove(name);
            if (depId != null) {
                try { esperService.undeploy(depId); }
                catch (Exception e) { log.error("Failed to undeploy '{}': {}", name, e.getMessage()); }
            }
        }
    }

    public Collection<EnforcementRule> getRules() { return rules.values(); }
    public void reset() { for (String name : List.copyOf(rules.keySet())) removeRule(name); }

    private String invertOperator(String op) {
        return switch (op) {
            case ">" -> "<="; case "<" -> ">=";
            case ">=" -> "<"; case "<=" -> ">";
            case "=" -> "!="; case "!=" -> "=";
            default -> { yield "!="; }
        };
    }
}
