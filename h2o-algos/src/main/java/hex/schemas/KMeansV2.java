package hex.schemas;

import hex.kmeans.KMeans;
import hex.kmeans.KMeansModel.KMeansParameters;
import water.api.API;
import water.api.ModelParametersSchema;
import water.fvec.Frame;

public class KMeansV2 extends ModelBuilderSchema<KMeans,KMeansV2,KMeansV2.KMeansParametersV2> {

  public static final class KMeansParametersV2 extends ModelParametersSchema<KMeansParameters, KMeansParametersV2> {
    static public String[] own_fields = new String[] { "K", "max_iters", "normalize", "seed", "init" };

    // Input fields
    @API(help = "Number of clusters", required = true)
    public int K;

    @API(help="Maximum training iterations.")
    public int max_iters;        // Max iterations

    @API(help = "Normalize columns", level = API.Level.secondary)
    public boolean normalize = true;

    @API(help = "RNG Seed", level = API.Level.expert /* tested, works: , dependsOn = {"K", "max_iters"} */ )
    public long seed;

    @API(help = "Initialization mode", values = { "None", "PlusPlus", "Furthest" }) // TODO: pull out of enum class. . .
    public KMeans.Initialization init;

    @Override public KMeansParametersV2 fillFromImpl(KMeansParameters parms) {
      super.fillFromImpl(parms);
      this.init = KMeans.Initialization.Furthest;
      return this;
    }

    public KMeansParameters fillImpl(KMeansParameters impl) {
      super.fillImpl(impl);
      impl._init = KMeans.Initialization.Furthest;
      return impl;
    }
  }

  //==========================
  // Custom adapters go here

  // TODO: UGH
  // Return a URL to invoke KMeans on this Frame
  @Override protected String acceptsFrame( Frame fr ) { return "/v2/KMeans?training_frame="+fr._key; }
}
