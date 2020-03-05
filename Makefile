GIT_SHA  = $(shell git rev-parse HEAD)
GCP_PROJECT ?= graphhopper-s1-e3ec


.PHONY: gcp-submit
gcp-submit:
	gcloud builds submit --project $(GCP_PROJECT) --substitutions COMMIT_SHA=$(GIT_SHA) --config cloudbuild.yaml
