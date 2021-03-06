package org.hcjf.layers.query;

import org.hcjf.log.Log;
import org.hcjf.properties.SystemProperties;

import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

/**
 * Collection of evaluator components.
 * @author javaito
 *
 */
public abstract class EvaluatorCollection {

    protected final Set<Evaluator> evaluators;
    private final EvaluatorCollection parent;

    public EvaluatorCollection() {
        this(null);
    }

    public EvaluatorCollection(EvaluatorCollection parent) {
        this.evaluators = new HashSet<>();
        this.parent = parent;
    }

    /**
     * Return the unmodifiable set with evaluators.
     * @return Evaluators.
     */
    public final Set<Evaluator> getEvaluators() {
        return Collections.unmodifiableSet(evaluators);
    }

    /**
     * Return the parent of the evaluator collection, the parent is other
     * instance of evaluator collection.
     * @return Parent of the collection, could be null.
     */
    public final EvaluatorCollection up() {
        EvaluatorCollection result = parent;
        if(result == null) {
            result = this;
        }
        return result;
    }

    /**
     * Add an instance of the evaluator object that evaluate if some instance of the
     * data collection must be in the result add or not.
     * @param evaluator FieldEvaluator instance.
     * @return Return the same instance of this class.
     * @throws IllegalArgumentException If the instance of the evaluator is null.
     */
    public final EvaluatorCollection addEvaluator(Evaluator evaluator) {
        if(evaluator == null) {
            throw new IllegalArgumentException("Null evaluator");
        }

        if(!evaluators.contains(evaluator)) {
            if(onAddEvaluator(evaluator)) {
                evaluators.add(checkEvaluator(evaluator));
            }
        } else {
            Log.w(SystemProperties.get(SystemProperties.Query.LOG_TAG),
                    "Duplicate evaluator: $s", evaluator);
        }
        return this;
    }

    /**
     * This method is called when some evaluator is added to the collection.
     * @param evaluator Evaluator added.
     * @return True if the evaluator must be added into the evaluator collection.
     */
    protected boolean onAddEvaluator(Evaluator evaluator) {
        return true;
    }

    private Query.QueryParameter checkQueryParameter(Query.QueryParameter queryParameter) {
        if(queryParameter instanceof Query.QueryField) {
            Query.QueryField queryField = (Query.QueryField) queryParameter;
            Query.QueryResource resource = queryField.getResource();
            if (resource == null) {
                EvaluatorCollection collection = parent;
                while (!(collection instanceof Query)) {
                    collection = collection.parent;
                }
                queryField.setResource(((Query) collection).getResource());
            }
        } else if(queryParameter instanceof Query.QueryFunction) {
            Query.QueryFunction function = (Query.QueryFunction) queryParameter;
            for(Object functionParameter : function.getParameters()) {
                if(functionParameter instanceof Query.QueryParameter) {
                    checkQueryParameter((Query.QueryParameter) functionParameter);
                }
            }
        }
        return queryParameter;
    }

    protected Evaluator checkEvaluator(Evaluator evaluator) {
        if(evaluator instanceof FieldEvaluator) {
            checkQueryParameter(((FieldEvaluator)evaluator).getQueryParameter());
        }
        return evaluator;
    }

    /**
     * Add a particular evaluator that implements 'distinct' method.
     * @param fieldName Name of the pair getter/setter to obtain the evaluable value
     * for each of data collection's object.
     * @param value Value to compare the field value of the instances.
     * @return Return the same instance of this class.
     */
    public final EvaluatorCollection distinct(String fieldName, Object value) {
        return addEvaluator(new Distinct(fieldName, value));
    }

    /**
     * Add a particular evaluator that implements 'equals' method.
     * @param fieldName Name of the pair getter/setter to obtain the evaluable value
     * for each of data collection's object.
     * @param value Value to compare the field value of the instances.
     * @return Return the same instance of this class.
     */
    public final EvaluatorCollection equals(String fieldName, Object value) {
        return addEvaluator(new Equals(fieldName, value));
    }

    /**
     * Add a particular evaluator that implements 'greater than' method.
     * @param fieldName Name of the pair getter/setter to obtain the evaluable value
     * for each of data collection's object.
     * @param value Value to compare the field value of the instances.
     * @return Return the same instance of this class.
     */
    public final EvaluatorCollection greaterThan(String fieldName, Object value) {
        return addEvaluator(new GreaterThan(fieldName, value));
    }

    /**
     * Add a particular evaluator that implements 'greater than or equals' method.
     * @param fieldName Name of the pair getter/setter to obtain the evaluable value
     * for each of data collection's object.
     * @param value Value to compare the field value of the instances.
     * @return Return the same instance of this class.
     */
    public final EvaluatorCollection greaterThanOrEquals(String fieldName, Object value) {
        return addEvaluator(new GreaterThanOrEqual(fieldName, value));
    }

    /**
     * Add a particular evaluator that implements 'in' method.
     * @param fieldName Name of the pair getter/setter to obtain the evaluable value
     * for each of data collection's object.
     * @param value Value to compare the field value of the instances.
     * @return Return the same instance of this class.
     */
    public final EvaluatorCollection in(String fieldName, Object value) {
        return addEvaluator(new In(fieldName, value));
    }

    /**
     * Add a particular evaluator that implements 'not in' method.
     * @param fieldName Name of the pair getter/setter to obtain the evaluable value
     * for each of data collection's object.
     * @param value Value to compare the field value of the instances.
     * @return Return the same instance of this class.
     */
    public final EvaluatorCollection notIn(String fieldName, Object value) {
        return addEvaluator(new NotIn(fieldName, value));
    }

    /**
     * Add a particular evaluator that implements 'smaller than' method.
     * @param fieldName Name of the pair getter/setter to obtain the evaluable value
     * for each of data collection's object.
     * @param value Value to compare the field value of the instances.
     * @return Return the same instance of this class.
     */
    public final EvaluatorCollection smallerThan(String fieldName, Object value) {
        return addEvaluator(new SmallerThan(fieldName, value));
    }

    /**
     * Add a particular evaluator that implements 'smaller than or equals' method.
     * @param fieldName Name of the pair getter/setter to obtain the evaluable value
     * for each of data collection's object.
     * @param value Value to compare the field value of the instances.
     * @return Return the same instance of this class.
     */
    public final EvaluatorCollection smallerThanOrEqual(String fieldName, Object value) {
        return addEvaluator(new SmallerThanOrEqual(fieldName, value));
    }

    /**
     * Add a particular evaluator that implements 'like' method.
     * @param fieldName Name of the pair getter/setter to obtain the evaluable value
     * for each of data collection's object.
     * @param value Value to compare the field value of the instances.
     * @return Return the same instance of this class.
     */
    public final EvaluatorCollection like(String fieldName, Object value) {
        return addEvaluator(new Like(fieldName, value));
    }

    /**
     * Add a group evaluator with 'or' function, by default return evaluate with false.
     * @return Return the instance of the new evaluator collection (or instance);
     */
    public final EvaluatorCollection or() {
        Or or = new Or(this);
        addEvaluator(or);
        return or;
    }

    /**
     * Add a group evaluator with 'and' function, by default return evaluate with false.
     * @return Return the instance of the new evaluator collection (and instance);
     */
    public final EvaluatorCollection and() {
        And and = new And(this);
        addEvaluator(and);
        return and;
    }
}
