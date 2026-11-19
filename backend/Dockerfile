# One image, three Cloud Run services (ROLE=api|worker|admin), ADR-09.
FROM golang:1.24-bookworm AS build
WORKDIR /src
COPY go.mod go.sum ./
RUN go mod download
COPY . .
RUN CGO_ENABLED=0 go build -trimpath -ldflags="-s -w" -o /out/sarkari ./cmd/sarkari

FROM gcr.io/distroless/static-debian12:nonroot
COPY --from=build /out/sarkari /sarkari
ENV ROLE=api APP_ENV=prod LOG_JSON=true
EXPOSE 8080
USER nonroot:nonroot
ENTRYPOINT ["/sarkari"]
CMD ["serve"]
