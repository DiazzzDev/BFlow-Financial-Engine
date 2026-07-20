CREATE TABLE features (
    id UUID NOT NULL DEFAULT gen_random_uuid(),
    code VARCHAR(100) NOT NULL UNIQUE,
    name VARCHAR(150) NOT NULL,
    limitable BOOLEAN NOT NULL,
    PRIMARY KEY (id)
);

CREATE TABLE plan_features (
    id UUID NOT NULL DEFAULT gen_random_uuid(),
    plan_id UUID NOT NULL,
    feature_id UUID NOT NULL,
    "limit" INTEGER,
    enabled BOOLEAN NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT uq_plan_feature UNIQUE (plan_id, feature_id),
    CONSTRAINT fk_plan_feature_plan FOREIGN KEY (plan_id) REFERENCES plans (id),
    CONSTRAINT fk_plan_feature_feature FOREIGN KEY (feature_id) REFERENCES features (id)
);