/*
 * Copyright © 2017 Cask Data, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not
 * use this file except in compliance with the License. You may obtain a copy of
 * the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations under
 * the License.
 */

package co.cask.wrangler.steps;

import co.cask.wrangler.api.AbstractStep;
import co.cask.wrangler.api.PipelineContext;
import co.cask.wrangler.api.Row;
import co.cask.wrangler.api.SkipRowException;
import co.cask.wrangler.api.StepException;
import org.apache.commons.jexl3.JexlBuilder;
import org.apache.commons.jexl3.JexlContext;
import org.apache.commons.jexl3.JexlEngine;
import org.apache.commons.jexl3.JexlException;
import org.apache.commons.jexl3.JexlScript;
import org.apache.commons.jexl3.MapContext;
import org.apache.commons.lang.StringUtils;

import java.util.HashMap;
import java.util.Map;

/**
 * A Wrangler step for apply an expression to store the result in a column.
 *
 * The expressions are specified in JEXL format (http://commons.apache.org/proper/commons-jexl/reference/syntax.html)
 * Step is response for executing only one expression for each {@link Row} record that is
 * being passed. The result of expression either adds a new column or replaces the value of
 * the existing column.
 *
 * <p>
 *   Step step = new Expression(lineno, directive, column, "if (age > 24 ) { 'adult' } else { 'teen' }");
 * </p>
 */
public class Expression extends AbstractStep {
  // Column to which the result of experience is applied to.
  private final String column;

  // The actual expression
  private final String expression;

  // Handler to Jexl Engine.
  private final JexlEngine engine;

  // Parsed / Compiled expression.
  private final JexlScript script;

  /**
   * Helper for performing basic house keeping operations.
   */
  public static class Convertors {
    public static double toDouble(String value) {
      return Double.parseDouble(value);
    }
    public static float toFloat(String value) {
      return Float.parseFloat(value);
    }
    public static long toLong(String value) {
      return Long.parseLong(value);
    }
    public static int toInt(String value) {
      return Integer.parseInt(value);
    }
    public static byte[] toBytes(String value) {
      return value.getBytes();
    }
    public static String concat(String a, String b) {
      return a.concat(b);
    }
    public static String concat(String a, String delim, String b) {
      return a.concat(delim).concat(b);
    }
  }

  public Expression(int lineno, String detail, String column, String expression) {
    super(lineno, detail);
    this.column = column;
    this.expression = expression;

    // Load the functions that should be accessible in the script.
    Map<String, Object> functions = new HashMap<>();
    functions.put(null, Convertors.class);
    functions.put("math", Math.class);
    functions.put("string", StringUtils.class);

    // Create and build the script.
    engine = new JexlBuilder().namespaces(functions).silent(false).cache(10).strict(true).create();
    script = engine.createScript(expression);
  }

  /**
   * Transforms a column value from any case to upper case.
   *
   * @param row Input {@link Row} to be wrangled by this step.
   * @param context Specifies the context of the pipeline.
   * @return Transformed {@link Row} in which the 'col' value is lower cased.
   * @throws StepException thrown when type of 'col' is not STRING.
   */
  @Override
  public Row execute(Row row, PipelineContext context) throws StepException, SkipRowException {
    Row modified = new Row(row);

    // Move the fields from the row into the context.
    JexlContext ctx = new MapContext();
    for (int i = 0; i < row.length(); ++i) {
      ctx.set(row.getColumn(i), row.getValue(i));
    }
    
    // Execution of the script / expression based on the row data
    // mapped into context.
    try {
      Object result = script.execute(ctx);
      int idx = modified.find(this.column);
      if (idx == -1) {
        modified.add(this.column, result.toString());
      } else {
        modified.setValue(idx, result.toString());
      }
    } catch (JexlException e) {
      // Generally JexlException wraps the original exception, so it's good idea
      // to check if there is a inner exception, if there is wrap it in 'StepException'
      // else just print the error message.
      if (e.getCause() != null) {
        throw new StepException(toString() + " : " + e.getMessage(), e.getCause());
      } else {
        throw new StepException(toString() + " : " + e.getMessage());
      }
    }
    return modified;
  }
}


