import { Component } from "react";

export default class ErrorBoundary extends Component {
  state = { error: null };

  static getDerivedStateFromError(error) {
    return { error };
  }

  componentDidCatch(error, info) {
    console.error("AutoStone UI error", error, info);
  }

  render() {
    if (!this.state.error) return this.props.children;
    return (
      <div className="legacy-page">
        <section className="legacy-panel error-boundary">
          <h1>AutoStone UI error</h1>
          <p>The page could not be rendered safely. Your data has not been deleted.</p>
          <pre>{this.state.error?.message || "Unexpected interface error"}</pre>
          <button className="legacy-btn primary" onClick={() => window.location.reload()}>
            Reload application
          </button>
        </section>
      </div>
    );
  }
}
