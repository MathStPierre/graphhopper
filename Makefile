GIT_SHA  = $(shell git rev-parse HEAD)
GCP_PROJECT ?= graphhopper-s1-e3ec
ARTIFACTORY_USER ?= user
ARTIFACTORY_PASS ?= pass
RELEASE_TAG = release-tag-test
RELEASE_VERSION = 1.1
DEV_VERSION = 1.2-CURBSIDE-SNAPSHOT



.PHONY: gcp-submit
gcp-submit:
	gcloud builds submit --project $(GCP_PROJECT) --substitutions COMMIT_SHA=$(GIT_SHA) --config cloudbuild.yaml

.PHONY: deploy-snapshot
deploy-snapshot:
	scripts/ci/perform-deploy-snapshot

.PHONY: release
release:
	scripts/ci/perform-release
