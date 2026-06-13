import { Navigate, Link } from 'react-router-dom';
import { useAuthStore } from '../shared/stores/authStore';

export function LandingPage() {
  const token = useAuthStore((s) => s.token);

  if (token) {
    return <Navigate to="/feed" replace />;
  }

  return (
    <div className="min-h-screen bg-gray-50 flex flex-col items-center justify-center p-4">
      <div className="max-w-2xl bg-white rounded-lg shadow-lg p-8 md:p-12 text-center border border-gray-100">
        <h1 className="text-4xl font-extrabold text-gray-900 mb-4">Claudium</h1>
        <p className="text-xl text-gray-700 mb-6">Портал осознанного подхода к жизни</p>
        <p className="text-lg text-gray-600 mb-8 leading-relaxed">
          Claudium — пространство для тех, кто хочет жить осознаннее: разбираться в себе, выстраивать отношения и разумно распоряжаться своими жизненными ресурсами. Здесь можно проработать личный вопрос один на один с ИИ-собеседником, обсудить конфликт с близким человеком под руководством нейтрального ИИ-медиатора и читать материалы авторов — о психологии, отношениях, привычках и балансе в жизни.
        </p>
        <div className="flex flex-col sm:flex-row gap-4 justify-center">
          <Link
            to="/register"
            className="px-8 py-3 bg-blue-600 text-white font-semibold rounded-lg hover:bg-blue-700 transition-colors"
          >
            Регистрация
          </Link>
          <Link
            to="/login"
            className="px-8 py-3 bg-gray-100 text-gray-800 font-semibold rounded-lg hover:bg-gray-200 transition-colors border border-gray-200"
          >
            Войти
          </Link>
        </div>
      </div>
    </div>
  );
}
