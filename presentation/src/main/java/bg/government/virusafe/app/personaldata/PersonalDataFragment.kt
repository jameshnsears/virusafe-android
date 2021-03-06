package bg.government.virusafe.app.personaldata

import android.annotation.SuppressLint
import android.content.Context
import android.content.res.ColorStateList
import android.view.MotionEvent
import android.view.View
import android.view.animation.CycleInterpolator
import android.view.animation.TranslateAnimation
import androidx.annotation.ColorRes
import androidx.appcompat.app.AlertDialog
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.Observer
import bg.government.virusafe.BR
import bg.government.virusafe.R
import bg.government.virusafe.app.home.Agreement
import bg.government.virusafe.app.home.HomeFragment
import bg.government.virusafe.app.home.OnDialogButtonListener
import bg.government.virusafe.app.location.LocationUpdateManager
import bg.government.virusafe.app.selfcheck.SelfCheckFragment
import bg.government.virusafe.app.utils.DATA_PROTECTION_NOTICE_SMALL_LBL
import bg.government.virusafe.app.utils.DENY_PERSONAL_DATA_MESSAGE
import bg.government.virusafe.app.utils.DPN_DESCRIPTION
import bg.government.virusafe.app.utils.DPN_TITLE
import bg.government.virusafe.app.utils.I_CONSENT_TO_LBL
import bg.government.virusafe.app.utils.NO_LABEL
import bg.government.virusafe.app.utils.PERMISSION_CHANGE_TXT
import bg.government.virusafe.app.utils.UPDATE_PERSONAL_INFO_TXT
import bg.government.virusafe.app.utils.YES_LABEL
import bg.government.virusafe.app.utils.getAgeInputFilter
import bg.government.virusafe.app.utils.getChronicConditionsInputFilter
import bg.government.virusafe.app.utils.setClickablePhrase
import bg.government.virusafe.databinding.FragmentPersonalDataBinding
import bg.government.virusafe.mvvm.fragment.AbstractFragment
import com.upnetix.applicationservice.registration.RegistrationServiceImpl.Companion.USE_PERSONAL_DATA_KEY
import com.upnetix.applicationservice.registration.model.Gender

class PersonalDataFragment :
	AbstractFragment<FragmentPersonalDataBinding, PersonalDataViewModel>(), OnDialogButtonListener {

	@SuppressLint("ClickableViewAccessibility")
	override fun onPrepareLayout(layoutView: View) {
		super.onPrepareLayout(layoutView)

		setInputFilters()
		binding.personalDataContainer.setOnClickListener {
			hideKeyboard()
		}

		binding.personalDataBtn.setOnClickListener {
			if (canClick().not()) return@setOnClickListener
			sendData()
		}

		getData()

		setDataProtectionNotice()
	}

	override fun onAgreeBtnClicked(agreement: Agreement) {
		setDataProtectionTxtColor(R.color.colorPrimary)
		binding.dataProtectionNoticeCheckBox.isChecked = true
	}

	private fun setDataProtectionNotice() {
		viewModel.isCheckBoxVisible = navigatedFromRegistration

		with(binding) {
			dataProtectionNoticeTxt.setClickablePhrase(
				fullText = buildString {
					append(viewModel.localizeString(I_CONSENT_TO_LBL))
					append(" ")
					append(viewModel.localizeString(DATA_PROTECTION_NOTICE_SMALL_LBL))
				},
				clickablePhrase = viewModel.localizeString(DATA_PROTECTION_NOTICE_SMALL_LBL),
				shouldBoldPhrase = false,
				shouldUnderlinePhrase = true
			) {
				if (!canClick()) {
					return@setClickablePhrase
				}

				showAgreementsDialog(
					viewModel.localizeString(DPN_TITLE),
					viewModel.localizeString(DPN_DESCRIPTION),
					Agreement.DataProtectionNotice,
					!binding.dataProtectionNoticeCheckBox.isChecked,
					this@PersonalDataFragment
				)
			}

			noticeTouchListener()
		}
	}

	@SuppressLint("ClickableViewAccessibility")
	private fun noticeTouchListener() {
		with(binding) {
			dataProtectionNoticeCheckBox.setOnTouchListener { view, event ->
				setDataProtectionTxtColor(R.color.colorPrimary)
				if (event.action == MotionEvent.ACTION_DOWN && dataProtectionNoticeCheckBox.isChecked &&
					!personalAgeEt.text.isNullOrBlank() && !personalNumberEt.text.isNullOrBlank()
				) {
					showDeletePersonalDataDialog(view.context)
					true
				} else {
					false
				}
			}
		}
	}

	private fun showDeletePersonalDataDialog(context: Context) {
		AlertDialog.Builder(context)
			.setMessage(viewModel.localizeString(DENY_PERSONAL_DATA_MESSAGE))
			.setCancelable(false)

			.setPositiveButton(viewModel.localizeString(YES_LABEL)) { _, _ ->
				viewModel.deletePersonalInformation()
			}

			.setNegativeButton(viewModel.localizeString(NO_LABEL)) { _, _ -> }
			.show()
	}

	private fun showWarningDialog(context: Context, flexKey: String) {
		AlertDialog.Builder(context)
			.setMessage(viewModel.localizeString(flexKey))
			.setCancelable(false)

			.setPositiveButton(viewModel.localizeString(YES_LABEL)) { _, _ ->
				sendUserData()
			}

			.setNegativeButton(viewModel.localizeString(NO_LABEL)) { _, _ ->
				binding.dataProtectionNoticeCheckBox.isChecked = false
			}
			.show()
	}

	private fun setDataProtectionTxtColor(@ColorRes color: Int) {
		binding.dataProtectionNoticeCheckBox.buttonTintList = ColorStateList.valueOf(
			ContextCompat.getColor(
				activity ?: return, color
			)
		)
		binding.dataProtectionNoticeTxt.setTextColor(
			ContextCompat.getColor(
				activity ?: return, color
			)
		)
		binding.dataProtectionNoticeTxt.setLinkTextColor(
			ContextCompat.getColor(
				activity ?: return, color
			)
		)
	}

	private fun setInputFilters() {
		binding.personalHealthStatusEt.filters = getChronicConditionsInputFilter()
		binding.personalAgeEt.filters = getAgeInputFilter()
	}

	override fun addViewModelObservers(viewLifecycleOwner: LifecycleOwner) {
		viewModel.gender.observe(viewLifecycleOwner, Observer {
			when (it) {
				Gender.Male -> binding.personalGenderContainer.check(binding.personalGenderMale.id)
				Gender.Female -> binding.personalGenderContainer.check(binding.personalGenderFemale.id)
				Gender.None -> binding.personalGenderContainer.clearCheck()
			}
		})

		viewModel.responseData.observe(viewLifecycleOwner, Observer { responseWrapper ->
			hideProgress()
			processResponse(responseWrapper)
		})

		viewModel.saveResponseData.observe(viewLifecycleOwner, Observer { responseWrapper ->
			hideProgress()
			processResponse(responseWrapper) {
				openNextScreen()
			}
		})

		viewModel.errorIdData.observe(viewLifecycleOwner, Observer { errorMsg ->
			binding.personalNumberLayout.error = errorMsg
		})

		viewModel.errorAgeData.observe(viewLifecycleOwner, Observer { errorMsg ->
			binding.personalAgeLayout.error = errorMsg
		})

		viewModel.deleteDataResponse.observe(viewLifecycleOwner, Observer { responseWrapper ->
			processResponse(responseWrapper) {
				context?.let { ctx -> LocationUpdateManager.getInstance(ctx).stopLocationUpdates() }

				with(binding) {
					personalNumberEt.text?.clear()
					personalAgeEt.text?.clear()
					personalGenderMale.isChecked = false
					personalGenderFemale.isChecked = false
					personalHealthStatusEt.text?.clear()
					dataProtectionNoticeCheckBox.isChecked = false
				}
			}
		})
	}

	private fun getData() {
		showProgress()
		viewModel.getData()
	}

	private fun sendData() {
		with(binding) {
			when {
				personalNumberLayout.error != null -> shakeError(personalNumberLayout)
				personalAgeLayout.error != null -> shakeError(personalAgeLayout)
				personalNumberEt.text.isNullOrBlank() -> this@PersonalDataFragment.viewModel.validatePersonalNumber()
				!dataProtectionNoticeCheckBox.isChecked -> setDataProtectionTxtColor(R.color.color_red)
				navigatedFromRegistration -> sendUserData()

				else -> {
					if (!sharedPrefsService.readStringFromSharedPrefs(USE_PERSONAL_DATA_KEY).toBoolean()) {
						showWarningDialog(root.context, PERMISSION_CHANGE_TXT)
					} else {
						showWarningDialog(root.context, UPDATE_PERSONAL_INFO_TXT)
					}
				}
			}
		}
	}

	private fun sendUserData() {
		hideProgress()
		viewModel.sendData()
	}

	private fun openNextScreen() {
		if (navigatedFromRegistration) {
			openFragmentFromRegistrationFlow(SelfCheckFragment::class)
		} else {
			navigateToView(HomeFragment::class)
		}
	}

	override fun onBack(): Boolean {
		super.onBack()
		return if (navigatedFromRegistration) {
			activity?.finish()
			true
		} else {
			false
		}
	}

	private fun shakeError(view: View) {
		val shakeAnim =
			TranslateAnimation(SHAKE_FROM_X, SHAKE_TO_X, SHAKE_FROM_Y, SHAKE_TO_Y).apply {
				duration = SHAKE_DURATION
				interpolator = CycleInterpolator(SHAKE_CYCLES)
			}
		view.startAnimation(shakeAnim)
	}

	override fun getLayoutResId(): Int = R.layout.fragment_personal_data

	override fun getViewModelClass(): Class<PersonalDataViewModel> =
		PersonalDataViewModel::class.java

	override fun getViewModelResId(): Int = BR.personalDataViewModel

	companion object {
		private const val SHAKE_DURATION = 500L
		private const val SHAKE_CYCLES = 7F
		private const val SHAKE_FROM_X = 0F
		private const val SHAKE_TO_X = 10F
		private const val SHAKE_FROM_Y = 0F
		private const val SHAKE_TO_Y = 0F
	}
}
