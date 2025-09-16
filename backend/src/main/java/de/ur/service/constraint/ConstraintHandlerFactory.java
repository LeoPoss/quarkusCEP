package de.ur.service.constraint;

import de.ur.dao.ConstraintType;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Instance;
import jakarta.inject.Inject;

import java.util.EnumMap;
import java.util.Map;

@ApplicationScoped
public class ConstraintHandlerFactory {
    private final Map<ConstraintType, ConstraintHandler> handlers = new EnumMap<>(ConstraintType.class);

    @Inject
    public ConstraintHandlerFactory(Instance<ConstraintHandler> handlerInstances) {
        for (ConstraintHandler handler : handlerInstances) {
            if (handler instanceof ResponseConstraintHandler) {
                handlers.put(ConstraintType.RESPONSE, handler);
            } else if (handler instanceof PrecedenceConstraintHandler) {
                handlers.put(ConstraintType.PRECEDENCE, handler);
            } else if (handler instanceof ExistenceConstraintHandler) {
                handlers.put(ConstraintType.EXISTENCE, handler);
            } else if (handler instanceof RespondedExistenceConstraintHandler) {
                handlers.put(ConstraintType.RESPONDED_EXISTENCE, handler);
            } else if (handler instanceof AlternateResponseConstraintHandler) {
                handlers.put(ConstraintType.ALTERNATE_RESPONSE, handler);
            } else if (handler instanceof ChainResponseConstraintHandler) {
                handlers.put(ConstraintType.CHAIN_RESPONSE, handler);
            } else if (handler instanceof AlternatePrecedenceConstraintHandler) {
                handlers.put(ConstraintType.ALTERNATE_PRECEDENCE, handler);
            } else if (handler instanceof NotResponseConstraintHandler) {
                handlers.put(ConstraintType.NOT_RESPONSE, handler);
            } else if (handler instanceof ChainPrecedenceConstraintHandler) {
                handlers.put(ConstraintType.CHAIN_PRECEDENCE, handler);
            }
        }
    }

    public ConstraintHandler getHandler(ConstraintType type) {
        ConstraintHandler handler = handlers.get(type);
        if (handler == null) {
            throw new IllegalArgumentException("No handler found for constraint type: " + type);
        }
        return handler;
    }
}
