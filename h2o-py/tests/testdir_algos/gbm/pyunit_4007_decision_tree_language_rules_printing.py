from __future__ import print_function
from builtins import range
import sys

from h2o.tree import H2OTree

sys.path.insert(1,"../../../")
import h2o
from tests import pyunit_utils, math
from h2o.estimators.gbm import H2OGradientBoostingEstimator


def decision_tree_language_rules_printing():
    print(" ----- numerical case: -----")
    df = h2o.import_file(path=pyunit_utils.locate("smalldata/logreg/prostate.csv"))
    df.describe()
    train = df.drop("ID")
    vol = train['VOL']
    vol[vol == 0] = None
    gle = train['GLEASON']
    gle[gle == 0] = None
    train['CAPSULE'] = train['CAPSULE'].asfactor()
    train.describe()
    my_gbm = H2OGradientBoostingEstimator(ntrees=50,
                                          learn_rate=0.1,
                                          distribution="bernoulli", max_depth = 2)
    my_gbm.train(x=list(range(1, train.ncol)),
                 y="CAPSULE",
                 training_frame=train,
                 validation_frame=train)
    first_tree = H2OTree(model = my_gbm, tree_number = 0, tree_class = None)

    print(" -- Tree predictions: -- ")
    print(first_tree.predictions)
    print(" -- Language tree representation: -- ")
    assert first_tree.language_tree_representation is not None
    print(first_tree.language_tree_representation)
    print(" -- Language path representation - root node: -- ")
    assert first_tree.language_path_representations[first_tree.root_node.id] is not None
    print(first_tree.language_path_representations[first_tree.root_node.id])
    print(" -- Language path representation - node ", first_tree.predictions.index(first_tree.predictions[3]), " (with pv = ", first_tree.predictions[3], "): -- ")
    assert first_tree.language_path_representations[first_tree.predictions.index(first_tree.predictions[3])] is not None
    print(first_tree.language_path_representations[first_tree.predictions.index(first_tree.predictions[3])])
    
    
    print(" ----- categorical case: -----")
    airlines_data = h2o.import_file(path=pyunit_utils.locate("smalldata/airlines/allyears2k_headers.zip"))
    model = H2OGradientBoostingEstimator(ntrees = 3, max_depth = 2)
    model.train(x=["Origin", "Distance"], y="IsDepDelayed", training_frame=airlines_data)
    tree = H2OTree(model = model, tree_number = 0, tree_class = "NO")

    print(" -- Tree predictions: -- ")
    print(tree.predictions)
    print(" -- Language tree representation: -- ")
    assert tree.language_tree_representation is not None
    print(tree.language_tree_representation)
    print(" -- Language path representation - root node: -- ")
    assert tree.language_path_representations[tree.root_node.id] is not None
    print(tree.language_path_representations[tree.root_node.id])
    print(" -- Language path representation - node ", tree.predictions.index(tree.predictions[3]), " (with pv = ", tree.predictions[3], "): -- ")
    assert tree.language_path_representations[tree.predictions.index(tree.predictions[3])] is not None
    print(tree.language_path_representations[tree.predictions.index(tree.predictions[3])])
    
if __name__ == "__main__":
    pyunit_utils.standalone_test(decision_tree_language_rules_printing)
else:
    decision_tree_language_rules_printing()
