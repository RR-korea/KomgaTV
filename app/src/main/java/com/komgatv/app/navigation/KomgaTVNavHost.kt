package com.komgatv.app.navigation

import androidx.compose.runtime.Composable
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.komgatv.feature.home.HomeScreen
import com.komgatv.feature.library.LibraryScreen
import com.komgatv.feature.library.SeriesDetailScreen
import com.komgatv.feature.reader.ReaderScreen
import com.komgatv.feature.settings.SettingsScreen

/**
 * 화면 라우트 정의
 *
 * sealed class를 사용하여 타입 안전한 네비게이션 라우트를 정의합니다.
 */
sealed class Screen(val route: String) {

    /** 홈 화면 - 계속 읽기, 최근 추가, 라이브러리 목록 */
    data object Home : Screen("home")

    /** 라이브러리 화면 - 시리즈 그리드 표시 */
    data object Library : Screen("library/{libraryId}") {
        fun createRoute(libraryId: String) = "library/$libraryId"
    }

    /** 시리즈 상세 화면 - 시리즈 정보 및 책 목록 */
    data object SeriesDetail : Screen("series/{seriesId}") {
        fun createRoute(seriesId: String) = "series/$seriesId"
    }

    /** 리더 화면 - 전체 화면 만화 페이지 뷰어 */
    data object Reader : Screen("reader/{bookId}") {
        fun createRoute(bookId: String) = "reader/$bookId"
    }

    /** 설정 화면 - 서버 연결 및 캐시 관리 */
    data object Settings : Screen("settings")
}

/**
 * KomgaTV 네비게이션 호스트
 *
 * 앱의 모든 화면에 대한 네비게이션 그래프를 구성합니다.
 * D-Pad 네비게이션에 최적화되어 있습니다.
 *
 * @param navController 네비게이션 컨트롤러
 */
@Composable
fun KomgaTVNavHost(
    navController: NavHostController = rememberNavController(),
) {
    NavHost(
        navController = navController,
        startDestination = Screen.Home.route,
    ) {
        // 홈 화면
        composable(Screen.Home.route) {
            HomeScreen(
                onNavigateToLibrary = { libraryId ->
                    navController.navigate(Screen.Library.createRoute(libraryId))
                },
                onNavigateToReader = { bookId ->
                    navController.navigate(Screen.Reader.createRoute(bookId))
                },
            )
        }

        // 라이브러리 화면
        composable(
            route = Screen.Library.route,
            arguments = listOf(
                navArgument("libraryId") { type = NavType.StringType }
            ),
        ) { backStackEntry ->
            val libraryId = backStackEntry.arguments?.getString("libraryId") ?: return@composable
            LibraryScreen(
                libraryId = libraryId,
                onNavigateToSeries = { seriesId ->
                    navController.navigate(Screen.SeriesDetail.createRoute(seriesId))
                },
                onNavigateBack = { navController.popBackStack() },
            )
        }

        // 시리즈 상세 화면
        composable(
            route = Screen.SeriesDetail.route,
            arguments = listOf(
                navArgument("seriesId") { type = NavType.StringType }
            ),
        ) { backStackEntry ->
            val seriesId = backStackEntry.arguments?.getString("seriesId") ?: return@composable
            SeriesDetailScreen(
                seriesId = seriesId,
                onNavigateToReader = { bookId ->
                    navController.navigate(Screen.Reader.createRoute(bookId))
                },
                onNavigateBack = { navController.popBackStack() },
            )
        }

        // 리더 화면
        composable(
            route = Screen.Reader.route,
            arguments = listOf(
                navArgument("bookId") { type = NavType.StringType }
            ),
        ) { backStackEntry ->
            val bookId = backStackEntry.arguments?.getString("bookId") ?: return@composable
            ReaderScreen(
                bookId = bookId,
                onNavigateBack = { navController.popBackStack() },
            )
        }

        // 설정 화면
        composable(Screen.Settings.route) {
            SettingsScreen(
                onNavigateBack = { navController.popBackStack() },
            )
        }
    }
}
