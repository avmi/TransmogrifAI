/*
 * Copyright (c) 2017, Salesforce.com, Inc.
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are met:
 *
 * * Redistributions of source code must retain the above copyright notice, this
 *   list of conditions and the following disclaimer.
 *
 * * Redistributions in binary form must reproduce the above copyright notice,
 *   this list of conditions and the following disclaimer in the documentation
 *   and/or other materials provided with the distribution.
 *
 * * Neither the name of the copyright holder nor the names of its
 *   contributors may be used to endorse or promote products derived from
 *   this software without specific prior written permission.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS"
 * AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE
 * IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE
 * DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT HOLDER OR CONTRIBUTORS BE LIABLE
 * FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL
 * DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR
 * SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER
 * CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY,
 * OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE
 * OF THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */

package com.salesforce.op.stages.impl.feature

import com.salesforce.op.OpWorkflow
import com.salesforce.op.features.types._
import com.salesforce.op.stages.base.unary.UnaryModel
import com.salesforce.op.test.{OpEstimatorSpec, TestFeatureBuilder}
import scala.util.{Failure, Success}

class StandardMinEstimatorTest extends OpEstimatorSpec[Real, UnaryModel[Real, Real], StandardMinEstimator]{

  val inputValues: Seq[Double] = Seq(10, 100, 1000)

  /**
   * Expected result of the transformer applied on the Input Dataset
   */
  override val expectedResult: Seq[Real] = {
    val expectedMean = inputValues.sum / inputValues.length
    val expectedStd = math.sqrt(inputValues.map(value => math.pow(expectedMean - value, 2)).sum
      / (inputValues.length - 1))
    val expectedMin = inputValues.min
    inputValues.map( value => (value - expectedMin) / expectedStd).toReal
  }

  val (inputData, testF) = TestFeatureBuilder(inputValues.map(_.toReal))

  override val estimator: StandardMinEstimator = new StandardMinEstimator().setInput(testF)

  it should "descale and work in standardized positive workflow" in {
    val featureNormalizer = new StandardMinEstimator().setInput(testF)
    val normedOutput = featureNormalizer.getOutput()
    val metadata = featureNormalizer.fit(inputData).getMetadata()

    val expectedMean = inputValues.sum / inputValues.length
    val expectedStd = math.sqrt(inputValues.map(value => math.pow(expectedMean - value, 2)).sum
      / (inputValues.length - 1))
    val expectedMin = inputValues.min
    val expectedSlope = 1 / expectedStd
    val expectedIntercept = - expectedMin / expectedStd
    ScalerMetadata(metadata) match {
      case Failure(err) => fail(err)
      case Success(meta) =>
        meta shouldBe a[ScalerMetadata]
        meta.scalingType shouldBe ScalingType.Linear
        meta.scalingArgs shouldBe a[LinearScalerArgs]
        math.abs((meta.scalingArgs.asInstanceOf[LinearScalerArgs].slope - expectedSlope)
          / expectedSlope) should be < 0.001
        math.abs((meta.scalingArgs.asInstanceOf[LinearScalerArgs].intercept - expectedIntercept)
          / expectedIntercept) should be < 0.001
    }

    val descaledResponse = new DescalerTransformer[Real, Real, Real]()
      .setInput(normedOutput, normedOutput).getOutput()
    val workflow = new OpWorkflow().setResultFeatures(descaledResponse)
    val wfModel = workflow.setInputDataset(inputData).train()
    val transformed = wfModel.score()

    val actual = transformed.collect().map(_.getAs[Double](1))
    val expected : Seq[Double] = inputValues
    all(actual.zip(expected).map(x => math.abs(x._2 - x._1))) should be < 0.0001
  }

}