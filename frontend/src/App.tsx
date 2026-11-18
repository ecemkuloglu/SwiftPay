import { Navigate, Route, Routes } from 'react-router';
import { ProtectedRoute } from './auth/ProtectedRoute';
import { Layout } from './components/Layout';
import { DashboardPage } from './pages/DashboardPage';
import { LoginPage } from './pages/LoginPage';
import { NewTransferPage } from './pages/NewTransferPage';
import { TransferDetailPage } from './pages/TransferDetailPage';
import { TransfersPage } from './pages/TransfersPage';

export function AppRoutes() {
  return (
    <Routes>
      <Route path="/login" element={<LoginPage />} />
      <Route element={<ProtectedRoute />}>
        <Route element={<Layout />}>
          <Route index element={<DashboardPage />} />
          <Route path="transfers" element={<TransfersPage />} />
          <Route path="transfers/new" element={<NewTransferPage />} />
          <Route path="transfers/:id" element={<TransferDetailPage />} />
        </Route>
      </Route>
      <Route path="*" element={<Navigate to="/" replace />} />
    </Routes>
  );
}
