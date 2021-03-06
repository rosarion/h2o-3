# This file is auto-generated by h2o-3/h2o-bindings/bin/gen_R.py
# Copyright 2016 H2O.ai;  Apache License Version 2.0 (see LICENSE for details) 
#'
# -------------------------- Target Encoder -------------------------- #
#'
#' Transformation of a categorical variable with a mean value of the target variable
#'
#' @param x (Optional) A vector containing the names or indices of the predictor variables to use in building the model.
#'        If x is missing, then all columns except y are used.
#' @param y The name or column index of the response variable in the data. 
#'        The response must be either a numeric or a categorical/factor variable. 
#'        If the response is numeric, then a regression model will be trained, otherwise it will train a classification model.
#' @param training_frame Id of the training data frame.
#' @param model_id Destination id for this model; auto-generated if not specified.
#' @param fold_column Column with cross-validation fold index assignment per observation.
#' @param blending \code{Logical}. Blending enabled/disabled Defaults to FALSE.
#' @param k Inflection point. Used for blending (if enabled). Blending is to be enabled separately using the 'blending'
#'        parameter. Defaults to 10.
#' @param f Smoothing. Used for blending (if enabled). Blending is to be enabled separately using the 'blending'
#'        parameter. Defaults to 20.
#' @param data_leakage_handling Data leakage handling strategy. Must be one of: "None", "KFold", "LeaveOneOut". Defaults to None.
#' @param noise_level Noise level Defaults to 0.01.
#' @param seed Seed for random numbers (affects certain parts of the algo that are stochastic and those might or might not be enabled by default).
#'        Defaults to -1 (time-based random number).
#' @examples
#' \dontrun{
#' library(h2o)
#' h2o.init()
#' #Import the titanic dataset
#' f <- "https://s3.amazonaws.com/h2o-public-test-data/smalldata/gbm_test/titanic.csv"
#' titanic <- h2o.importFile(f)
#' 
#' # Set response as a factor
#' response <- "survived"
#' titanic[response] <- as.factor(titanic[response])
#' 
#' # Split the dataset into train and test
#' splits <- h2o.splitFrame(data = titanic, ratios = .8, seed = 1234)
#' train <- splits[[1]]
#' test <- splits[[2]]
#' 
#' # Choose which columns to encode
#' encode_columns <- c("home.dest", "cabin", "embarked")
#' 
#' # Train a TE model
#' te_model <- h2o.targetencoder(x = encode_columns,
#'                               y = response, 
#'                               training_frame = train,
#'                               fold_column = "pclass", 
#'                               data_leakage_handling = "KFold")
#' 
#' # New target encoded train and test sets
#' train_te <- h2o.transform(te_model, train)
#' test_te <- h2o.transform(te_model, test)
#' }
#' @export
h2o.targetencoder <- function(x,
                              y,
                              training_frame,
                              model_id = NULL,
                              fold_column = NULL,
                              blending = FALSE,
                              k = 10,
                              f = 20,
                              data_leakage_handling = c("None", "KFold", "LeaveOneOut"),
                              noise_level = 0.01,
                              seed = -1)
{
  # Validate required training_frame first and other frame args: should be a valid key or an H2OFrame object
  training_frame <- .validate.H2OFrame(training_frame, required=TRUE)

  # Validate other required args
  # If x is missing, then assume user wants to use all columns as features.
  if (missing(x)) {
     if (is.numeric(y)) {
         x <- setdiff(col(training_frame), y)
     } else {
         x <- setdiff(colnames(training_frame), y)
     }
  }

  # Build parameter list to send to model builder
  parms <- list()
  args <- .verify_dataxy(training_frame, x, y)
  if( !missing(fold_column) && !is.null(fold_column)) args$x_ignore <- args$x_ignore[!( fold_column == args$x_ignore )]
  parms$ignored_columns <- args$x_ignore
  parms$response_column <- args$y
  parms$training_frame <- training_frame

  if (!missing(model_id))
    parms$model_id <- model_id
  if (!missing(fold_column))
    parms$fold_column <- fold_column
  if (!missing(blending))
    parms$blending <- blending
  if (!missing(k))
    parms$k <- k
  if (!missing(f))
    parms$f <- f
  if (!missing(data_leakage_handling))
    parms$data_leakage_handling <- data_leakage_handling
  if (!missing(noise_level))
    parms$noise_level <- noise_level
  if (!missing(seed))
    parms$seed <- seed

  # Error check and build model
  model <- .h2o.modelJob('targetencoder', parms, h2oRestApiVersion=3, verbose=FALSE)
  return(model)
}
.h2o.train_segments_targetencoder <- function(x,
                                              y,
                                              training_frame,
                                              fold_column = NULL,
                                              blending = FALSE,
                                              k = 10,
                                              f = 20,
                                              data_leakage_handling = c("None", "KFold", "LeaveOneOut"),
                                              noise_level = 0.01,
                                              seed = -1,
                                              segment_columns = NULL,
                                              segment_models_id = NULL,
                                              parallelism = 1)
{
  # formally define variables that were excluded from function parameters
  model_id <- NULL
  verbose <- NULL
  destination_key <- NULL
  # Validate required training_frame first and other frame args: should be a valid key or an H2OFrame object
  training_frame <- .validate.H2OFrame(training_frame, required=TRUE)

  # Validate other required args
  # If x is missing, then assume user wants to use all columns as features.
  if (missing(x)) {
     if (is.numeric(y)) {
         x <- setdiff(col(training_frame), y)
     } else {
         x <- setdiff(colnames(training_frame), y)
     }
  }

  # Build parameter list to send to model builder
  parms <- list()
  args <- .verify_dataxy(training_frame, x, y)
  if( !missing(fold_column) && !is.null(fold_column)) args$x_ignore <- args$x_ignore[!( fold_column == args$x_ignore )]
  parms$ignored_columns <- args$x_ignore
  parms$response_column <- args$y
  parms$training_frame <- training_frame

  if (!missing(fold_column))
    parms$fold_column <- fold_column
  if (!missing(blending))
    parms$blending <- blending
  if (!missing(k))
    parms$k <- k
  if (!missing(f))
    parms$f <- f
  if (!missing(data_leakage_handling))
    parms$data_leakage_handling <- data_leakage_handling
  if (!missing(noise_level))
    parms$noise_level <- noise_level
  if (!missing(seed))
    parms$seed <- seed

  # Build segment-models specific parameters
  segment_parms <- list()
  if (!missing(segment_columns))
    segment_parms$segment_columns <- segment_columns
  if (!missing(segment_models_id))
    segment_parms$segment_models_id <- segment_models_id
  segment_parms$parallelism <- parallelism

  # Error check and build segment models
  segment_models <- .h2o.segmentModelsJob('targetencoder', segment_parms, parms, h2oRestApiVersion=3)
  return(segment_models)
}
