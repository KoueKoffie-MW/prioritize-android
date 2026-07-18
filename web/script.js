document.addEventListener('DOMContentLoaded', () => {
    // Sliders & Inputs
    valImportanceSlider = document.getElementById('importance-slider');
    valUrgencySlider = document.getElementById('urgency-slider');
    valDurationSelect = document.getElementById('duration-select');
    valDeadlineSelect = document.getElementById('deadline-select');

    // Values Display
    lblImportance = document.getElementById('val-importance');
    lblUrgency = document.getElementById('val-urgency');
    lblPriorityScore = document.getElementById('priority-score-val');
    lblBaseScore = document.getElementById('breakdown-base');
    lblDeadlineScore = document.getElementById('breakdown-deadline');
    lblDopamineBonus = document.getElementById('breakdown-dopamine');

    function calculateScore() {
        const importance = parseInt(valImportanceSlider.value, 10);
        const urgency = parseInt(valUrgencySlider.value, 10);
        const durationMin = parseInt(valDurationSelect.value, 10);
        const deadlineVal = valDeadlineSelect.value;

        // Update slider label displays
        lblImportance.textContent = importance;
        lblUrgency.textContent = urgency;

        // Base Score calculation
        const baseScore = (3.0 * importance) + (2.0 * urgency);
        lblBaseScore.textContent = baseScore.toFixed(1);

        const durationHours = durationMin / 60.0;
        let deadlineScore = 0.0;
        let slackTimeHours = Infinity;

        if (deadlineVal !== 'none') {
            const timeRemainingHours = parseInt(deadlineVal, 10);
            
            // Assume soft deadline logic (2 days grace period)
            const graceDays = 2;
            const effectiveTimeRemaining = timeRemainingHours + (graceDays * 24);
            
            slackTimeHours = effectiveTimeRemaining - durationHours;

            if (slackTimeHours <= 0) {
                deadlineScore = 60.0;
            } else {
                deadlineScore = (72.0 / (slackTimeHours + 6.0)) * 5.0;
            }
        }
        lblDeadlineScore.textContent = deadlineScore.toFixed(1);

        // Dopamine quick win bonus
        const isNotUrgent = deadlineVal === 'none' || slackTimeHours > 24.0;
        let dopamineBonus = 0.0;
        if (isNotUrgent) {
            if (durationMin <= 15) {
                dopamineBonus = 10.0;
            } else if (durationMin <= 60) {
                dopamineBonus = 5.0;
            }
        }
        lblDopamineBonus.textContent = dopamineBonus.toFixed(1);

        // Total
        const totalScore = baseScore + deadlineScore + dopamineBonus;
        lblPriorityScore.textContent = totalScore.toFixed(1);

        // Dynamic dial color changes based on priority intensity
        const dial = document.querySelector('.score-dial');
        if (totalScore >= 45.0) {
            dial.style.borderColor = '#CF6679'; // Red
            dial.style.boxShadow = '0 0 20px rgba(207, 102, 121, 0.4)';
            lblPriorityScore.style.color = '#CF6679';
        } else if (totalScore >= 30.0) {
            dial.style.borderColor = '#FFB74D'; // Orange
            dial.style.boxShadow = '0 0 20px rgba(255, 183, 77, 0.4)';
            lblPriorityScore.style.color = '#FFB74D';
        } else if (totalScore >= 20.0) {
            dial.style.borderColor = '#BB86FC'; // Purple
            dial.style.boxShadow = '0 0 20px rgba(187, 134, 252, 0.4)';
            lblPriorityScore.style.color = '#BB86FC';
        } else {
            dial.style.borderColor = '#03DAC6'; // Mint
            dial.style.boxShadow = '0 0 20px rgba(3, 218, 198, 0.2)';
            lblPriorityScore.style.color = '#03DAC6';
        }
    }

    // Attach Event Listeners
    valImportanceSlider.addEventListener('input', calculateScore);
    valUrgencySlider.addEventListener('input', calculateScore);
    valDurationSelect.addEventListener('change', calculateScore);
    valDeadlineSelect.addEventListener('change', calculateScore);

    // Initial Calculation
    calculateScore();
});
