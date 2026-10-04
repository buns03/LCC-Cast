package lccast.voting.system.dto;

public class VerifyEmailResponse {

    private boolean success;
    private String message;
    private String maskedEmail;

    public static VerifyEmailResponse success(String maskedEmail) {
        VerifyEmailResponse response = new VerifyEmailResponse();
        response.success = true;
        response.maskedEmail = maskedEmail;
        return response;
    }

    public static VerifyEmailResponse failure(String message) {
        VerifyEmailResponse response = new VerifyEmailResponse();
        response.success = false;
        response.message = message;
        return response;
    }

    public boolean isSuccess() {
        return success;
    }

    public void setSuccess(boolean success) {
        this.success = success;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }

    public String getMaskedEmail() {
        return maskedEmail;
    }

    public void setMaskedEmail(String maskedEmail) {
        this.maskedEmail = maskedEmail;
    }
}
