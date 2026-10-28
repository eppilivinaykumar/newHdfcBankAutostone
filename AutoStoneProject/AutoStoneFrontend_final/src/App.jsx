import { BrowserRouter, Routes, Route, NavLink, Link } from "react-router-dom";
import DashboardPage from "./pages/DashboardPage.jsx";
import JobsPage from "./pages/JobsPage.jsx";
import MonitoringPage from "./monitoring-ui/MonitoringPage.jsx";
import QuickViewPage from "./pages/QuickViewPage.jsx";
import JobDetailPage from "./pages/JobDetailPage.jsx";
import JobFormPage from "./pages/JobFormPage.jsx";
import ImportJilPage from "./pages/ImportJilPage.jsx";
import ServersPage from "./pages/ServersPage.jsx";
import AlertPoliciesPage from "./pages/AlertPoliciesPage.jsx";
import WorkflowsPage from "./pages/WorkflowsPage.jsx";
import ErrorBoundary from "./components/ErrorBoundary.jsx";

const nav = [
  ["/", "Dashboard", true],
  ["/monitoring", "Monitoring"],
  ["/quick-view", "Quick View"],
  ["/jobs", "Jobs"],
  ["/workflows", "Workflows"],
  ["/servers", "Servers"],
  ["/alert-policies", "Alert Policies"],
  ["/import-jil", "Import JIL"],
];

export default function App() {
  return (
    <BrowserRouter>
      <div className="legacy-shell">
        <header className="legacy-header">
          <div className="brand-lockup"><span className="ca-mark">AS</span><span>AutoStone Workload Automation</span></div>
          <div className="header-user">Admin <span>│</span> My Profile <span>│</span> Help</div>
        </header>
        <div className="primary-nav">
          {nav.map(([to, label]) => <NavLink key={to} to={to} end={to === "/"}>{label}</NavLink>)}
          <div className="nav-spacer" />
          <Link className="new-job-link" to="/jobs/new">New Job</Link>
        </div>
        <ErrorBoundary>
        <Routes>
          <Route path="/" element={<DashboardPage />} />
          <Route path="/monitoring" element={<MonitoringPage />} />
          <Route path="/jobs" element={<JobsPage />} />
          <Route path="/quick-view" element={<QuickViewPage />} />
          <Route path="/jobs/:id" element={<JobDetailPage />} />
          <Route path="/jobs/new" element={<JobFormPage />} />
          <Route path="/jobs/:id/edit" element={<JobFormPage />} />
          <Route path="/import-jil" element={<ImportJilPage />} />
          <Route path="/servers" element={<ServersPage />} />
          <Route path="/alert-policies" element={<AlertPoliciesPage />} />
          <Route path="/workflows" element={<WorkflowsPage />} />
        </Routes>
        </ErrorBoundary>
        <footer className="legacy-footer">© AutoStone Enterprise Workload Automation</footer>
      </div>
    </BrowserRouter>
  );
}
