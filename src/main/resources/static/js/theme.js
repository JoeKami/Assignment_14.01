// Function to initialize theme based on window-specific preference
function initializeTheme() {
    const windowId = getWindowId();
    if (!windowId) return;

    // Get theme preference from session storage with window-specific key
    const isDarkMode = sessionStorage.getItem(`darkMode_${windowId}`) === 'true';
    updateTheme(isDarkMode);
}

// Function to toggle dark mode
function toggleDarkMode() {
    const windowId = getWindowId();
    if (!windowId) return;

    const isDarkMode = document.body.classList.toggle('dark-mode');
    // Store theme preference in session storage with window-specific key
    sessionStorage.setItem(`darkMode_${windowId}`, isDarkMode);
    updateThemeIcon(isDarkMode);
}

// Function to update theme icon
function updateThemeIcon(isDarkMode) {
    const themeToggle = document.querySelector('.theme-toggle i');
    const themeText = document.querySelector('#themeToggleText');
    if (themeToggle) {
        themeToggle.className = isDarkMode ? 'fas fa-sun' : 'fas fa-moon';
    }
    if (themeText) {
        themeText.textContent = isDarkMode ? 'Light Mode' : 'Dark Mode';
    }
}

// Function to update theme
function updateTheme(isDarkMode) {
    if (isDarkMode) {
        document.body.classList.add('dark-mode');
    } else {
        document.body.classList.remove('dark-mode');
    }
    updateThemeIcon(isDarkMode);
}

// Function to get window ID from URL
function getWindowId() {
    const urlParams = new URLSearchParams(window.location.search);
    return urlParams.get('windowId');
}

// Initialize theme when DOM is loaded
document.addEventListener('DOMContentLoaded', initializeTheme); 