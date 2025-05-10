let isUserTyping = false;
let refreshTimeout;
const REFRESH_INTERVAL = 2000; // 2 seconds

// Auto-scroll to bottom of messages
function scrollToBottom() {
    const messagesContainer = document.getElementById('messages');
    if (messagesContainer) {
        messagesContainer.scrollTop = messagesContainer.scrollHeight;
    }
}

// Initialize chat functionality
function initializeChat() {
    // Scroll to bottom on page load
    scrollToBottom();

    // Handle input focus
    const messageInput = document.querySelector('.message-form input');
    if (messageInput) {
        messageInput.addEventListener('focus', function() {
            isUserTyping = true;
            clearTimeout(refreshTimeout);
        });

        messageInput.addEventListener('blur', function() {
            isUserTyping = false;
            scheduleRefresh();
        });

        // Handle form submission
        const messageForm = document.querySelector('.message-form');
        if (messageForm) {
            messageForm.addEventListener('submit', function(e) {
                if (!messageInput.value.trim()) {
                    e.preventDefault();
                }
            });
        }
    }

    // Initial refresh schedule
    scheduleRefresh();
}

function scheduleRefresh() {
    if (!isUserTyping) {
        refreshTimeout = setTimeout(function() {
            const currentUrl = new URL(window.location.href);
            // Add timestamp to prevent caching
            currentUrl.searchParams.set('t', new Date().getTime());
            window.location.href = currentUrl.href;
        }, REFRESH_INTERVAL);
    }
}

// Clear refresh timeout when leaving the page
window.addEventListener('beforeunload', function() {
    clearTimeout(refreshTimeout);
});

// Initialize chat when DOM is loaded
document.addEventListener('DOMContentLoaded', initializeChat);

// Dropdown functionality
function toggleDropdown() {
    const dropdown = document.getElementById('userDropdown');
    const bubble = document.querySelector('.user-bubble');
    dropdown.classList.toggle('show');
    bubble.classList.toggle('active');
}

// Close dropdown when clicking outside
document.addEventListener('click', function(event) {
    const dropdown = document.getElementById('userDropdown');
    const bubble = document.querySelector('.user-bubble');
    if (!bubble?.contains(event.target) && !dropdown?.contains(event.target)) {
        dropdown?.classList.remove('show');
        bubble?.classList.remove('active');
    }
}); 