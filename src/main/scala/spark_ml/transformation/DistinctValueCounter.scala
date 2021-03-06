/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package spark_ml.transformation

import spire.implicits._
import scala.collection.mutable
import org.apache.spark.rdd.RDD

object DistinctValueCounter {
  /*
   * Get distinct values for selected columns of a tabular text data (e.g. csv/tsv/etc.)
   * Besides an RDD of String, it takes an array of column indices as an input and
   * sets of distinct values per column index as an output.
   */
  def getDistinctValues(
    data: RDD[String],
    delimiter: String,
    headerExistsInRDD: Boolean,
    colIndices: Array[Int]): mutable.Map[Int, mutable.Set[String]] = {

    // For each mapper, we generate a set of distinct values per column and
    // at the end we reduce multiple sets of distinct values per column into a single set per column.
    data.mapPartitionsWithIndex((partitionIdx: Int, lines: Iterator[String]) => {
      val distinctValsPerCol: mutable.Map[Int, mutable.Set[String]] = mutable.Map[Int, mutable.Set[String]]()
      cfor(0)(_ < colIndices.length, _ + 1)(
        i => distinctValsPerCol.put(colIndices(i), mutable.Set[String]())
      )

      // Drop the first line of the first partition if there's a header.
      if (headerExistsInRDD && partitionIdx == 0) {
        lines.drop(1)
      }

      lines.foreach((line: String) => {
        val lineElems = line.split(delimiter, -1)
        cfor(0)(_ < colIndices.length, _ + 1)(
          i => {
            val colVal = lineElems(colIndices(i)).trim

            // Even empty strings are treated as a unique value.
            distinctValsPerCol(colIndices(i)).add(colVal)
          }
        )
      })

      Array[mutable.Map[Int, mutable.Set[String]]](distinctValsPerCol).toIterator
    }).reduce((m1: mutable.Map[Int, mutable.Set[String]], m2: mutable.Map[Int, mutable.Set[String]]) => {
      m2.keys.foreach((idx: Int) => {
        if (!m1.contains(idx)) {
          m1.put(idx, mutable.Set[String]())
        }

        m1(idx) ++= m2(idx)
      })

      m1
    })
  }

  /**
   * Map a set of distinct values to an increasing non-negative numbers.
   * E.g., {'Women' -> 0, 'Men' -> 1}, etc.
   * @param distinctValues A set of distinct values for different columns (first key is index to a column).
   * @param leaveEmptyString Leave empty strings out (do not map them to numbers).
   * @return A map of distinct values to integers for different columns (first key is index to a column).
   */
  def mapDistinctValuesToIntegers(
    distinctValues: mutable.Map[Int, mutable.Set[String]],
    leaveEmptyString: Boolean): mutable.Map[Int, mutable.Map[String, Int]] = {
    val distinctValMapsToInts = mutable.Map[Int, mutable.Map[String, Int]]()
    distinctValues.foreach(index_values => {
      val index = index_values._1
      val values = index_values._2
      val mapsToInts = mutable.Map[String, Int]()
      distinctValMapsToInts.put(index, mapsToInts)
      var intVal = 0
      values.foreach(value => {
        if (!(value == "" && leaveEmptyString)) {
          mapsToInts.put(value, intVal)
          intVal += 1
        }
      })
    })

    distinctValMapsToInts
  }
}
