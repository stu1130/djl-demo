package org.example;

import java.io.IOException;
import java.nio.file.Paths;

import ai.djl.Device;
import ai.djl.Model;
import ai.djl.basicdataset.Mnist;
import ai.djl.basicmodelzoo.basic.Mlp;
import ai.djl.metric.Metrics;
import ai.djl.ndarray.types.Shape;
import ai.djl.nn.Block;
import ai.djl.training.DefaultTrainingConfig;
import ai.djl.training.Trainer;
import ai.djl.training.dataset.Batch;
import ai.djl.training.dataset.Dataset;
import ai.djl.training.dataset.RandomAccessDataset;
import ai.djl.training.evaluator.Accuracy;
import ai.djl.training.listener.TrainingListener;
import ai.djl.training.loss.Loss;
import ai.djl.training.util.ProgressBar;
import ai.djl.ui.listener.UiTrainingListener;

public final class MnistTraining {

    private static final String MODEL_DIR = "target/models/mnist";
    private static final String MODEL_NAME = "mlp";

    public static void main(String[] args) throws IOException {
        // Construct neural network
        Block block = new Mlp(Mnist.IMAGE_HEIGHT * Mnist.IMAGE_WIDTH, Mnist.NUM_CLASSES, new int[]{128, 64});

        try (Model model = Model.newInstance()) {
            model.setBlock(block);

            // get training and validation dataset
            RandomAccessDataset trainingSet = prepareDataset(Dataset.Usage.TRAIN, 64, Long.MAX_VALUE);
            RandomAccessDataset validateSet = prepareDataset(Dataset.Usage.TEST, 64, Long.MAX_VALUE);

            // setup training configuration
            DefaultTrainingConfig config = new DefaultTrainingConfig(Loss.softmaxCrossEntropyLoss())
                    .addEvaluator(new Accuracy()).optDevices(Device.getDevices(Device.getGpuCount()))
                    .addTrainingListeners(new UiTrainingListener())
                    .addTrainingListeners(TrainingListener.Defaults.logging());

            try (Trainer trainer = model.newTrainer(config)) {
                trainer.setMetrics(new Metrics());

                Shape inputShape = new Shape(1, Mnist.IMAGE_HEIGHT * Mnist.IMAGE_WIDTH);

                // initialize trainer with proper input shape
                trainer.initialize(inputShape);
                fit(trainer, 10, trainingSet, validateSet, MODEL_DIR, MODEL_NAME);
            }
            model.save(Paths.get(MODEL_DIR), MODEL_NAME);
        }
    }

    private static void fit(Trainer trainer, int numEpoch, Dataset trainingSet, Dataset validateSet, String outputDir, String modelName) throws IOException {
        for (int epoch = 0; epoch < numEpoch; epoch++) {
            for (Batch batch : trainer.iterateDataset(trainingSet)) {
                trainer.trainBatch(batch);
                trainer.step();
                batch.close();
            }

            if (validateSet != null) {
                for (Batch batch : trainer.iterateDataset(validateSet)) {
                    trainer.validateBatch(batch);
                    batch.close();
                }
            }
            // reset training and validation evaluators at end of epoch
            trainer.endEpoch();
            // save model at end of each epoch
            if (outputDir != null) {
                Model model = trainer.getModel();
                model.setProperty("Epoch", String.valueOf(epoch));
                model.save(Paths.get(outputDir), modelName);
            }
        }
    }

    private static RandomAccessDataset prepareDataset(Dataset.Usage usage, int batchSize, long limit) throws IOException {
        Mnist mnist = Mnist.builder().optUsage(usage).setSampling(batchSize, true).optLimit(limit).build();
        mnist.prepare(new ProgressBar());
        return mnist;
    }

}