///*
// active-learning-scala: Active Learning library for Scala
// Copyright (c) 2014 Davi Pereira dos Santos
//
//   This program is free software: you can redistribute it and/or modify
//   it under the terms of the GNU General Public License as published by
//   the Free Software Foundation, either version 3 of the License, or
//   (at your option) any later version.
//
//   This program is distributed in the hope that it will be useful,
//   but WITHOUT ANY WARRANTY; without even the implied warranty of
//   MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
//   GNU General Public License for more details.
//
//   You should have received a copy of the GNU General Public License
//   along with this program.  If not, see <http://www.gnu.org/licenses/>.
// */
//
//package al.strategies
//
//import clean.res.passiveAccBal
//import ml.Pattern
//import ml.classifiers.Learner
//import ml.models.Model
//
//case class PassiveGme(learner: Learner, pool: Seq[Pattern], debug: Boolean = false)
//   extends StrategyWithLearner {
//   override val toString = "PassiveGme"
//   val abr = "Pasg"
//   val id = 23
//   override val mea = passiveAccBal()
//   if (learner.id > 3) error("Passive needs learner with full queries: C45, NB or 5NN.")
//
//   def next(current_model: Model, unlabeled: Seq[Pattern], labeled: Seq[Pattern]) = {
//      error("Passive cannot generate queries!")
//      unlabeled.head
//   }
//}
//
