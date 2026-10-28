package de.mrt.rich2euro

import android.content.Context
import android.net.Uri
import androidx.test.core.app.ApplicationProvider
import de.mrt.rich2euro.data.InMemoryApiKeyStore
import de.mrt.rich2euro.model.CoinSide
import de.mrt.rich2euro.ui.CoinAnalyzerViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class ExampleRobolectricTest {

    private val testDispatcher = UnconfinedTestDispatcher()

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `read app_name and package identity from context`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val appName = context.getString(R.string.app_name)
        assertEquals("Rich2Euro", appName)
        assertEquals("de.mrt.rich2euro", context.packageName)
        assertEquals("de.mrt.rich2euro", BuildConfig.APPLICATION_ID)
    }

    @Test
    fun `startup prompts for API key, stores primary and optional backup key, and unlocks analysis`() = runTest {
        val keyStore = InMemoryApiKeyStore(initialPrimaryKey = null)
        val viewModel = CoinAnalyzerViewModel(apiKeyStore = keyStore)

        assertTrue(viewModel.uiState.value.isApiKeyInitialized)
        assertTrue(viewModel.uiState.value.shouldShowApiKeyScreen)
        assertFalse(viewModel.uiState.value.canAnalyze)

        viewModel.saveApiKey(
            rawKey = "AIzaSyTestUserPersonalToken1234567890",
            rawBackupKey = "gsk_OptionalFastBackupVisionToken123456"
        )
        assertFalse(viewModel.uiState.value.shouldShowApiKeyScreen)
        assertTrue(viewModel.uiState.value.hasValidSavedApiKey)
        assertEquals("gsk_OptionalFastBackupVisionToken123456", viewModel.uiState.value.backupApiKey)

        val frontUri = Uri.parse("content://test/coin_front.jpg")
        val backUri = Uri.parse("content://test/coin_back.jpg")

        viewModel.setCoinImage(CoinSide.OBVERSE, frontUri)
        assertFalse(viewModel.uiState.value.canAnalyze)

        viewModel.setCoinImage(CoinSide.REVERSE, backUri)
        assertTrue(viewModel.uiState.value.canAnalyze)

        viewModel.swapCoinSides()
        assertEquals(backUri, viewModel.uiState.value.obverseUri)
        assertEquals(frontUri, viewModel.uiState.value.reverseUri)

        viewModel.deleteSavedApiKey()
        assertTrue(viewModel.uiState.value.shouldShowApiKeyScreen)
        assertNull(viewModel.uiState.value.apiKey)
        assertNull(viewModel.uiState.value.backupApiKey)
        assertFalse(viewModel.uiState.value.canAnalyze)
    }
}
