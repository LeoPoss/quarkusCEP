package de.ur.service.constraint;

import de.ur.dao.ConstraintType;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Instance;
import jakarta.inject.Inject;

import java.util.EnumMap;
import java.util.Map;

@ApplicationScoped
public class ConstraintHandlerFactory {
    private final Map<ConstraintType, ConstraintHandler> handlers;

    @Inject
    public ConstraintHandlerFactory(Instance<ConstraintHandler> handlerInstances) {
        this.handlers = new EnumMap<>(ConstraintType.class);
        for (ConstraintHandler handler : handlerInstances) {
            ConstraintType type = handler.getType();
            handlers.put(type, handler);
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
