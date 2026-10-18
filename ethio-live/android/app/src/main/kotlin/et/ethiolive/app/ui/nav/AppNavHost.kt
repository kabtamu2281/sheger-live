package et.ethiolive.app.ui.nav

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import et.ethiolive.app.AppContainer
import et.ethiolive.app.MainActivity
import et.ethiolive.app.ui.screens.BlockedUsersScreen
import et.ethiolive.app.ui.screens.ConversationScreen
import et.ethiolive.app.ui.screens.CreatorDashboardScreen
import et.ethiolive.app.ui.screens.DeleteAccountScreen
import et.ethiolive.app.ui.screens.EditProfileScreen
import et.ethiolive.app.ui.screens.EventScreen
import et.ethiolive.app.ui.screens.EventsScreen
import et.ethiolive.app.ui.screens.ExploreScreen
import et.ethiolive.app.ui.screens.FanClubEditScreen
import et.ethiolive.app.ui.screens.FanClubScreen
import et.ethiolive.app.ui.screens.FollowsScreen
import et.ethiolive.app.ui.screens.GoLiveScreen
import et.ethiolive.app.ui.screens.HelpScreen
import et.ethiolive.app.ui.screens.HomeScreen
import et.ethiolive.app.ui.screens.LanguageScreen
import et.ethiolive.app.ui.screens.LedgerScreen
import et.ethiolive.app.ui.screens.LegalScreen
import et.ethiolive.app.ui.screens.LiveRoomScreen
import et.ethiolive.app.ui.screens.MeScreen
import et.ethiolive.app.ui.screens.MessagesScreen
import et.ethiolive.app.ui.screens.NotificationSettingsScreen
import et.ethiolive.app.ui.screens.NotificationsScreen
import et.ethiolive.app.ui.screens.PayoutScreen
import et.ethiolive.app.ui.screens.PrivacyScreen
import et.ethiolive.app.ui.screens.ProfileScreen
import et.ethiolive.app.ui.screens.RankingsScreen
import et.ethiolive.app.ui.screens.SafetyCenterScreen
import et.ethiolive.app.ui.screens.SettingsScreen
import et.ethiolive.app.ui.screens.StreamSettingsScreen
import et.ethiolive.app.ui.screens.WalletScreen

/** Navigation callbacks shared by screens, so screens never depend on the NavController directly. */
class Nav(private val controller: NavHostController) {
    fun back() { controller.popBackStack() }
    fun to(route: String) { controller.navigate(route) { launchSingleTop = true } }
    fun room(id: String) = to(Routes.room(id))
    fun profile(id: String) = to(Routes.profile(id))
    fun conversation(id: String) = to(Routes.conversation(id))
    /** After going live the setup screen is replaced by the room so "back" leaves the room. */
    fun replaceWithRoom(id: String) {
        controller.navigate(Routes.room(id)) { popUpTo(Routes.GO_LIVE) { inclusive = true } }
    }
}

@Composable
fun AppNavHost(controller: NavHostController, container: AppContainer, activity: MainActivity, modifier: Modifier = Modifier) {
    val nav = Nav(controller)
    NavHost(controller, startDestination = Routes.HOME, modifier = modifier) {
        composable(Routes.HOME) { HomeScreen(container, nav) }
        composable(Routes.EXPLORE) { ExploreScreen(container, nav) }
        composable(Routes.GO_LIVE) { GoLiveScreen(container, nav) }
        composable(Routes.MESSAGES) { MessagesScreen(container, nav) }
        composable(Routes.ME) { MeScreen(container, nav) }
        composable(Routes.ROOM) { LiveRoomScreen(container, nav, activity, it.arguments?.getString("id").orEmpty()) }
        composable(Routes.PROFILE) { ProfileScreen(container, nav, it.arguments?.getString("id").orEmpty()) }
        composable(Routes.FOLLOWS) {
            FollowsScreen(container, nav, it.arguments?.getString("id").orEmpty(), it.arguments?.getString("type") == "followers")
        }
        composable(Routes.CONVERSATION) { ConversationScreen(container, nav, it.arguments?.getString("id").orEmpty()) }
        composable(Routes.NOTIFICATIONS) { NotificationsScreen(container, nav) }
        composable(Routes.WALLET) { WalletScreen(container, nav, activity) }
        composable(Routes.LEDGER) { LedgerScreen(container, nav) }
        composable(Routes.CREATOR) { CreatorDashboardScreen(container, nav) }
        composable(Routes.PAYOUT) { PayoutScreen(container, nav) }
        composable(Routes.FAN_CLUB) { FanClubScreen(container, nav, it.arguments?.getString("id").orEmpty()) }
        composable(Routes.FAN_CLUB_EDIT) { FanClubEditScreen(container, nav) }
        composable(Routes.RANKINGS) { RankingsScreen(container, nav) }
        composable(Routes.EVENTS) { EventsScreen(container, nav) }
        composable(Routes.EVENT) { EventScreen(container, nav, it.arguments?.getString("id").orEmpty()) }
        composable(Routes.SETTINGS) { SettingsScreen(container, nav) }
        composable(Routes.EDIT_PROFILE) { EditProfileScreen(container, nav) }
        composable(Routes.PRIVACY) { PrivacyScreen(container, nav) }
        composable(Routes.NOTIFICATION_SETTINGS) { NotificationSettingsScreen(container, nav) }
        composable(Routes.BLOCKED) { BlockedUsersScreen(container, nav) }
        composable(Routes.LANGUAGE) { LanguageScreen(container, nav) }
        composable(Routes.LEGAL) { LegalScreen(nav, it.arguments?.getString("doc").orEmpty()) }
        composable(Routes.HELP) { HelpScreen(container, nav) }
        composable(Routes.SAFETY) { SafetyCenterScreen(container, nav) }
        composable(Routes.DELETE_ACCOUNT) { DeleteAccountScreen(container, nav) }
        composable(Routes.STREAM_SETTINGS) { StreamSettingsScreen(container, nav) }
    }
}
