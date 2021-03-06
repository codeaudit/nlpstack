package org.allenai.nlpstack.parse.poly.fsm

import org.allenai.nlpstack.parse.poly.ml.{ FeatureName, FeatureVector }

import scala.annotation.tailrec

/** A TrainingVector is a triple of the form (task, featureVector, transition), where
  * `task` is the ClassificationTask associated with the feature vector (`featureVector`), and
  * `transition` is the correct classification of the feature vector.
  *
  * These labeled feature vectors are used to train classifiers.
  */
case class FSMTrainingVector(task: ClassificationTask, transition: StateTransition,
    transitionSystem: TransitionSystem, state: State) {

  lazy val featureVector: FeatureVector = transitionSystem.computeFeature(state)

  override def toString: String = s"$transition: $featureVector"
}

abstract class FSMTrainingVectorSource(
    transitionSystemFactory: TransitionSystemFactory,
    baseCostFunctionFactory: Option[StateCostFunctionFactory]
) {

  def getVectorIterator: Iterator[FSMTrainingVector]

  /** Returns the set of tasks associated with the training vectors in this source.
    *
    * In a perhaps over-careful attempt to avoid having all the non-uniqued tasks being stored in
    * memory simultaneously, this was originally implemented as:
    *
    * format: OFF
    * lazy val tasks: Iterable[ClassificationTask] = taskHelper(Set(), getVectorIterator)
    * tailrec private def taskHelper(
    *   resultSoFar: Set[ClassificationTask],
    *   vectorIter: Iterator[FSMTrainingVector]
    * ): Set[ClassificationTask] = {
    *
    *  if (!vectorIter.hasNext) {
    *    resultSoFar
    *  } else {
    *    taskHelper(resultSoFar + vectorIter.next().task, vectorIter)
    *  }
    * }
    * format: ON
    *
    */
  lazy val tasks: Set[ClassificationTask] = getVectorIterator.map(_.task).toSet

  def groupVectorIteratorsByTask: Iterator[(ClassificationTask, Iterator[FSMTrainingVector])] = {
    tasks.iterator map { task =>
      println(s"Finding next task vectors.")
      (task, getVectorIterator filter { vector => vector.task == task })
    }
  }

  /** This generates a list of labeled feature vectors from a gold parse tree (for training). The
    * gold parse tree is reduced to its representation as a list of 2*n transitions, then a
    * TrainingVector is produced for each transition (in order).
    *
    * Note that this function is implemented using tail-recursion.
    *
    * @param sculpture the sculpture to generate feature vectors from
    * @return a list of training vectors
    */
  protected def generateVectors(sculpture: Sculpture): List[FSMTrainingVector] = {
    val transitionSystem =
      transitionSystemFactory.buildTransitionSystem(sculpture.marbleBlock, Set())
    val baseCostFunction =
      baseCostFunctionFactory map { fact => fact.buildCostFunction(sculpture.marbleBlock, Set())}
    transitionSystem.guidedCostFunction(sculpture) match {
      case Some(costFunction) =>
        val search = new GreedySearch(costFunction)
        val initialState = transitionSystem.initialState(Seq())
        initialState flatMap { initState => search.find(initState, Set()) } match {
          case Some(walk) => generateVectorsHelper(
            transitionSystem,
            baseCostFunction,
            walk.steps map {
              _.transition
            },
            initialState, List()
          ).reverse
          case None => List()
        }
      case None => List()
    }
  }

  @tailrec private def generateVectorsHelper(
    transitionSystem: TransitionSystem,
    baseCostFunction: Option[StateCostFunction],
    transitions: Seq[StateTransition],
    initState: Option[State],
    trainingVectorsSoFar: List[FSMTrainingVector]
  ): List[FSMTrainingVector] = {

    initState match {
      case None => trainingVectorsSoFar
      case Some(initialState) =>
        val task = transitionSystem.taskIdentifier(initialState)
        if (transitions.isEmpty) {
          trainingVectorsSoFar
        } else {
          val nextTransition: StateTransition = {
            baseCostFunction match {
              case None => transitions.head
              case Some(costFunc) => costFunc.lowestCostTransition(initialState) match {
                case None => transitions.head
                case Some(transition) =>
                  if (transition == transitions.head) {
                    Fallback
                  } else {
                    transitions.head
                  }
              }
            }
          }
          generateVectorsHelper(transitionSystem, baseCostFunction,
            transitions.tail, (transitions.head)(initState),
            FSMTrainingVector(task.get, nextTransition, transitionSystem, initialState)
              +: trainingVectorsSoFar)
        }
    }
  }
}

object FSMTrainingVectorSource {

  /** Collects the set of all transitions referred to in a source of training vectors.
    *
    * @param trainingVectorSource the source of training vectors
    * @return the set of all transitions referred to in the training vectors
    */
  def collectTransitions(trainingVectorSource: FSMTrainingVectorSource): Set[StateTransition] = {
    (trainingVectorSource.getVectorIterator map { _.transition }).toSet
  }

  /** Collects the set of all feature names referred to in a source of training vectors.
    * Note that a feature name is implemented as a List of Symbols.
    *
    * @param trainingVectorSource the source of training vectors
    * @return the set of all feature names referred to in the training vectors
    */
  def collectFeatureNames(trainingVectorSource: FSMTrainingVectorSource): Set[FeatureName] = {
    val trainingVectorIter = trainingVectorSource.getVectorIterator
    (for {
      trainingVector <- trainingVectorIter
      (featureName, featureValue) <- trainingVector.featureVector.values
    } yield featureName).toSet
  }
}

