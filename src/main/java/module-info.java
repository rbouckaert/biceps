open module biceps {
    requires transitive beast.pkgmgmt;
    requires transitive beast.base;
    requires transitive beast.fx;
    requires transitive javafx.controls;
	requires java.desktop;
	requires itextpdf;
	requires org.apache.commons.statistics.distribution;


    exports biceps;
    exports biceps.spec;
    exports biceps.operators;
    exports biceps.spec.operators;
    exports biceps.tools;

    provides beast.base.core.BEASTInterface with
    biceps.BICEPS,
    biceps.BICEPSPopulationLogProducer,
    biceps.EpochTreeDistribution,
    biceps.operators.EpochFlexOperator,
    biceps.operators.TreeStretchOperator,
    biceps.YuleSkyline,
    biceps.YuleSkylineLogProducer,

    biceps.spec.BICEPS,
    biceps.spec.BICEPSPopulationLogProducer,
    biceps.spec.EpochTreeDistribution,
    biceps.spec.operators.EpochFlexOperator,
    biceps.spec.operators.TreeStretchOperator,
    biceps.spec.YuleSkyline,
    biceps.spec.YuleSkylineLogProducer
;
}
