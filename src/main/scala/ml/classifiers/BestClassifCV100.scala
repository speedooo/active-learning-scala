/*
mls: basic machine learning algorithms for Scala
Copyright (C) 2014 Davi Pereira dos Santos

  This program is free software: you can redistribute it and/or modify
  it under the terms of the GNU General Public License as published by
  the Free Software Foundation, either version 3 of the License, or
  (at your option) any later version.

  This program is distributed in the hope that it will be useful,
  but WITHOUT ANY WARRANTY; without even the implied warranty of
  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
  GNU General Public License for more details.

  You should have received a copy of the GNU General Public License
  along with this program.  If not, see <http://www.gnu.org/licenses/>.
*/
package ml.classifiers

import al.strategies.Strategy
import clean.lib.{CM, Ds}
import ml.Pattern
import ml.models.Model
import util.Datasets

case class BestClassifCV100(ds: Ds, r: Int, f: Int, s: Strategy, queries: Seq[Pattern], fqueries: Seq[Pattern], seed: Int, poolForKNN: Seq[Pattern]) extends Learner with CM {
   override lazy val toString = s"BestLearnerCV100: $ds"
   lazy val id = ds.read(s"select c from classif100 where s=${s.id} and l=${s.learner.id} and r=$r and f=$f") match {
      case List(Vector(x)) => x.toInt
      case List() =>
         val res = classif.id
         ds.write(s"insert into classif100 values (${s.id},${s.learner.id},$r,$f,$res)")
         res
      case x => ds.error(s"problemas: $x")
   }
   lazy val abr: String = classif.abr
   lazy val attPref: String = classif.attPref
   lazy val boundaryType: String = classif.boundaryType
   lazy val learners = Seq(
      KNNBatcha(5, "eucl", poolForKNN, weighted = true)
      , C45()
      , RF(seed)
      , NBBatch()
      , CIELMBatch(seed)
      , SVMLibRBF(seed)
   )
   lazy val classif = learners.maxBy { l =>
      val qs = if (qf(l)) fqueries.toVector else queries.toVector
      Datasets.kfoldCV(qs, 5) { (tr, ts, foldnr, minsize) =>
         kappa(l.build(tr).confusion(ts)) //não pode ser accBal porque pode não haver um exemplo por classe
      }.sum
   }
   override lazy val querFiltro = qf(classif)

   def update(model: Model, fast_mutable: Boolean, semcrescer: Boolean)(pattern: Pattern) = classif.update(model, fast_mutable, semcrescer)(pattern)

   def expected_change(model: Model)(pattern: Pattern) = classif.expected_change(model)(pattern)

   def build(pool: Seq[Pattern]) = classif.build(pool)

   val context: String = "bestcv100"
}
