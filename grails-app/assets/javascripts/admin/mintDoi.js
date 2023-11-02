/**
 * Created by mol109 on 26/04/2016.
 */
"use strict;"

$(function () {
    $('#mintDoiSubmit').removeAttr('disabled');
    $('#spinner').hide();

    $('#mintDoiForm').validationEngine('attach', {scroll: false});

    $("#mintDoiSubmit").click(function (e) {

        $("#mintDoiSubmit").attr('disabled', 'disabled');

        var valid = $('#mintDoiForm').validationEngine('validate');

        if (valid) {
            $('#spinner').show();
            $("form[name='mintDoiForm']").submit();
        } else {
            $('#mintDoiSubmit').removeAttr('disabled');
            e.preventDefault();
        }
    });

    $('#existingDoiRadio').click(function () {
        $('#existingDoi').removeAttr("disabled");
        $('#providerMetadata').attr("disabled", "disabled");
    });

    $('#newDoiRadio').click(function () {
        $('#existingDoi').attr("disabled", "disabled");
        $('#providerMetadata').removeAttr("disabled");
    });
});

function isJson(field, rules, i, options) {
    try {
        if (field.val().trim()) {
            JSON.parse(field.val());
        }
    }
    catch (err) {
        console.warn(err.message);
        console.warn('Field:"' + field.val().trim() + '"');
        return "This field has to be a valid JSON document";
    }
}
