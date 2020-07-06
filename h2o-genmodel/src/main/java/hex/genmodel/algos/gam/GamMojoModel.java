package hex.genmodel.algos.gam;

public class GamMojoModel extends GamMojoModelBase {
  String _link;
  double _tweedieLinkPower;
  private boolean _binomial;
  
  GamMojoModel(String[] columns, String[][] domains, String responseColumn) {
    super(columns, domains, responseColumn);
  }
  
  void init() {
    super.init();
    _binomial = _family.equals("binomial") || _family.equals("fractionalbinomial") || _family.equals("quasibinomial");
  }
  
  // generate prediction for binomial/fractional binomial/negative binomial, poisson, tweedie families
  @Override
  double[] gamScore0(double[] data, double[] preds) {
    if (data.length == nfeatures())  // centered data, use center coefficients
      _beta = _beta_center;
    else  // use non-centering coefficients
      _beta = _beta_no_center;

    double eta = generateEta(_beta, data);  // generate eta, inner product of beta and data
    double mu = evalLink(eta, _link);

    if (_binomial) {
      preds[0] = (mu >= _defaultThreshold) ? 1 : 0; // threshold given by ROC
      preds[1] = 1.0 - mu; // class 0
      preds[2] =       mu; // class 1
    } else {
      preds[0] = mu;
    }
    return preds;
  }
}
