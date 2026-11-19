// Command sarkari is the single Sarkari Match backend binary (ADR-09: one image, three Cloud Run services that
// differ only by ROLE). Subcommands:
//
//	sarkari serve              run the HTTP server for ROLE=api|worker|admin|all (all = local dev, plus a tick loop)
//	sarkari migrate up|down|status|reset
//	sarkari tick <name>        run one worker tick (topics|jobs|housekeep) and exit
//	sarkari catalog rebuild    build unbuilt catalog batches and a fresh snapshot
//	sarkari admin add <email> <owner|reviewer>
package main

import (
	"context"
	"encoding/json"
	"errors"
	"fmt"
	"log/slog"
	"net/http"
	"os"
	"os/signal"
	"strings"
	"syscall"
	"time"

	"github.com/amanroy3901/job-vac/backend/internal/app"
	"github.com/amanroy3901/job-vac/backend/internal/config"
	"github.com/amanroy3901/job-vac/backend/internal/db"
)

func main() {
	if err := run(os.Args[1:]); err != nil {
		slog.Error("fatal", "err", err)
		os.Exit(1)
	}
}

func usage() error {
	return errors.New("usage: sarkari serve | migrate up|down|status|reset | tick <name> | catalog rebuild | admin add <email> <role>")
}

func run(args []string) error {
	if len(args) == 0 {
		args = []string{"serve"}
	}
	cfg, err := config.Load()
	if err != nil {
		return err
	}
	setupLogging(cfg)
	ctx, stop := signal.NotifyContext(context.Background(), syscall.SIGINT, syscall.SIGTERM)
	defer stop()

	switch args[0] {
	case "migrate":
		cmd := "up"
		if len(args) > 1 {
			cmd = args[1]
		}
		return db.Migrate(ctx, cfg.DatabaseURL, cmd)
	case "serve", "tick", "catalog", "admin":
	default:
		return usage()
	}

	pool, err := db.Open(ctx, cfg.DatabaseURL, cfg.DBMaxConns)
	if err != nil {
		return err
	}
	defer pool.Close()
	a, err := app.New(ctx, cfg, pool, app.Overrides{})
	if err != nil {
		return err
	}

	switch args[0] {
	case "tick":
		if len(args) < 2 {
			return usage()
		}
		out, err := a.Worker.Tick(ctx, args[1])
		if err != nil {
			return err
		}
		return json.NewEncoder(os.Stdout).Encode(out)
	case "catalog":
		if len(args) < 2 || args[1] != "rebuild" {
			return usage()
		}
		if _, err := a.Worker.RunJobs(ctx, 1000); err != nil {
			return err
		}
		seq, err := a.Publisher.BuildSnapshot(ctx)
		if err != nil {
			return err
		}
		fmt.Printf("catalog rebuilt; snapshot seq %d\n", seq)
		return nil
	case "admin":
		if len(args) != 4 || args[1] != "add" || (args[3] != "owner" && args[3] != "reviewer") {
			return usage()
		}
		_, err := pool.Exec(ctx, `INSERT INTO admin_user (email, role) VALUES (lower($1), $2) ON CONFLICT (email) DO UPDATE SET role = EXCLUDED.role`, args[2], args[3])
		return err
	}

	// serve
	srv := &http.Server{Addr: cfg.Addr, Handler: a.Handler, ReadHeaderTimeout: 10 * time.Second,
		ReadTimeout: 30 * time.Second, WriteTimeout: 60 * time.Second, IdleTimeout: 120 * time.Second}
	if cfg.Role == "all" || (cfg.Role == "worker" && !cfg.IsProd()) {
		go a.Worker.Run(ctx, cfg.TickInterval) // local only; Cloud Scheduler drives prod
	}
	errc := make(chan error, 1)
	go func() {
		slog.Info("listening", "addr", cfg.Addr, "role", cfg.Role, "env", cfg.Env)
		errc <- srv.ListenAndServe()
	}()
	select {
	case err := <-errc:
		return err
	case <-ctx.Done():
	}
	slog.Info("shutting down", "grace", cfg.ShutdownWait)
	sctx, cancel := context.WithTimeout(context.Background(), cfg.ShutdownWait)
	defer cancel()
	if err := srv.Shutdown(sctx); err != nil {
		return err
	}
	if err := <-errc; !errors.Is(err, http.ErrServerClosed) {
		return err
	}
	return nil
}

func setupLogging(cfg *config.Config) {
	var lvl slog.Level
	if err := lvl.UnmarshalText([]byte(strings.ToUpper(cfg.LogLevel))); err != nil {
		lvl = slog.LevelInfo
	}
	opts := &slog.HandlerOptions{Level: lvl, ReplaceAttr: func(_ []string, a slog.Attr) slog.Attr {
		// Cloud Logging field names.
		switch a.Key {
		case slog.LevelKey:
			a.Key = "severity"
		case slog.MessageKey:
			a.Key = "message"
		}
		return a
	}}
	var h slog.Handler = slog.NewTextHandler(os.Stderr, opts)
	if cfg.LogJSON {
		h = slog.NewJSONHandler(os.Stderr, opts)
	}
	slog.SetDefault(slog.New(h))
}
