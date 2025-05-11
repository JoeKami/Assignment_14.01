// Global variables
let isUserTyping = false;
let typingTimeout = null;
let eventSource = null;
let reconnectAttempts = 0;
let currentWindowId = null;
let messageInput = null;
let isSubmitting = false;
let lastMessageId = 0;
let shouldAutoScroll = true; // New variable to track if we should auto-scroll
let lastActivityTime = new Date();
const MAX_RECONNECT_ATTEMPTS = 5;
const RECONNECT_DELAY = 1000; // 1 second
const SCROLL_THRESHOLD = 100; // pixels from bottom to consider "near bottom"
const ACTIVE_USERS_TIMEOUT = 300000; // 30 seconds to consider a user inactive

// Auto-scroll to bottom of messages
function scrollToBottom() {
    const messagesContainer = document.getElementById('messages');
    if (messagesContainer) {
        messagesContainer.scrollTop = messagesContainer.scrollHeight;
    }
}

// Check if user is near bottom of messages
function isNearBottom() {
    const messagesContainer = document.getElementById('messages');
    if (!messagesContainer) return false;
    
    const threshold = SCROLL_THRESHOLD;
    const distanceFromBottom = messagesContainer.scrollHeight - messagesContainer.scrollTop - messagesContainer.clientHeight;
    return distanceFromBottom < threshold;
}

// Handle scroll events to update auto-scroll behavior
function handleScroll() {
    const messagesContainer = document.getElementById('messages');
    if (!messagesContainer) return;
    
    // Update shouldAutoScroll based on scroll position
    shouldAutoScroll = isNearBottom();
}

// Update last activity time
function updateLastActivityTime() {
    const lastActivityElement = document.getElementById('lastActivityTime');
    if (!lastActivityElement) return;

    const now = new Date();
    const diff = now - lastActivityTime;
    
    if (diff < 60000) { // Less than 1 minute
        lastActivityElement.textContent = 'Just now';
    } else if (diff < 3600000) { // Less than 1 hour
        const minutes = Math.floor(diff / 60000);
        lastActivityElement.textContent = `${minutes}m ago`;
    } else if (diff < 86400000) { // Less than 1 day
        const hours = Math.floor(diff / 3600000);
        lastActivityElement.textContent = `${hours}h ago`;
    } else {
        const days = Math.floor(diff / 86400000);
        lastActivityElement.textContent = `${days}d ago`;
    }
}

// Update active users count
function updateActiveUsersCount(count) {
    const activeUsersElement = document.getElementById('activeUsersCount');
    if (activeUsersElement) {
        activeUsersElement.textContent = count;
    }
}

// Initialize chat functionality
function initializeChat() {
    // Get windowId and channelId from URL
    const urlParams = new URLSearchParams(window.location.search);
    currentWindowId = urlParams.get('windowId');
    
    // Check if channel ID exists in the URL path
    const pathParts = window.location.pathname.split('/');
    const channelIdFromPath = pathParts[pathParts.indexOf('channel') + 1];
    
    if (!channelIdFromPath) {
        console.error('Channel ID missing from URL path');
        window.location.href = '/error/no-window-id';
        return;
    }
    
    const channelId = document.querySelector('.chat-header h2')?.getAttribute('data-channel-id');
    
    // Validate both windowId and channelId
    if (!currentWindowId || !channelId) {
        console.error('Missing windowId or channelId in URL');
        window.location.href = '/error/no-window-id';
        return;
    }
    
    // Validate channelId is a number and matches the URL path
    if (isNaN(parseInt(channelId)) || channelId !== channelIdFromPath) {
        console.error('Invalid channelId format or mismatch with URL');
        window.location.href = '/error/no-window-id';
        return;
    }

    // Initialize message input and focus handling
    messageInput = document.querySelector('input[name="content"]');
    if (messageInput) {
        messageInput.focus();
        messageInput.addEventListener('input', handleTyping);
        messageInput.addEventListener('blur', stopTyping);
    }

    // Initialize lastMessageId with the highest message ID on page load
    const messages = document.querySelectorAll('.message-sent, .message-received');
    messages.forEach(message => {
        const messageId = parseInt(message.getAttribute('data-message-id') || '0');
        lastMessageId = Math.max(lastMessageId, messageId);
    });

    // Set up message form handling
    const messageForm = document.getElementById('messageForm');
    if (messageForm) {
        messageForm.addEventListener('submit', handleMessageSubmit);
    }

    // Set up scroll event listener
    const messagesContainer = document.getElementById('messages');
    if (messagesContainer) {
        messagesContainer.addEventListener('scroll', handleScroll);
    }

    // Set up SSE connection
    setupEventSource();

    // Set up visibility change handling
    document.addEventListener('visibilitychange', handleVisibilityChange);

    // Initial scroll to bottom
    scrollToBottom();

    // Set up periodic updates for last activity time
    setInterval(updateLastActivityTime, 60000); // Update every minute
    updateLastActivityTime(); // Initial update
}

function setupEventSource() {
    if (eventSource) {
        eventSource.close();
    }
    
    const channelId = document.querySelector('.chat-header h2')?.getAttribute('data-channel-id');
    if (!channelId || !currentWindowId) {
        console.error('Missing channelId or currentWindowId for SSE setup', { channelId, currentWindowId });
        return;
    }
    
    eventSource = new EventSource(`/channel/${channelId}/events?windowId=${currentWindowId}`);
    
    eventSource.onopen = () => {
        console.log('SSE connection established');
        reconnectAttempts = 0;
    };
    
    eventSource.onerror = (error) => {
        console.error('SSE connection error:', error);
        eventSource.close();
        
        // Check if the error is due to authentication or not found
        if (error.status === 401 || error.status === 403 || error.status === 404) {
            window.location.href = '/error/no-window-id';
            return;
        }
        
        if (reconnectAttempts < MAX_RECONNECT_ATTEMPTS) {
            reconnectAttempts++;
            setTimeout(setupEventSource, RECONNECT_DELAY * reconnectAttempts);
        } else {
            console.error('Max reconnection attempts reached');
            showNotification('Connection lost. Please refresh the page.', 'error');
        }
    };
    
    // Handle different event types
    eventSource.addEventListener('connected', (event) => {
        console.log('Connected to channel events');
    });
    
    eventSource.addEventListener('new_message', (event) => {
        refreshMessages();
    });
    
    eventSource.addEventListener('channel_updated', (event) => {
        const data = JSON.parse(event.data);
        if (data.channelId === channelId) {
            refreshMessages();
        }
    });
    
    eventSource.addEventListener('channel_deleted', (event) => {
        const data = JSON.parse(event.data);
        if (data.channelId === channelId) {
            showNotification('This channel has been deleted', 'error');
            setTimeout(() => {
                window.location.href = `/welcome?windowId=${currentWindowId}`;
            }, 2000);
        }
    });
}

function handleVisibilityChange() {
    if (document.hidden) {
        if (eventSource) {
            eventSource.close();
            eventSource = null;
        }
    } else {
        setupEventSource();
        refreshMessages();
    }
}

function toggleDropdown(event) {
    if (event) {
        event.stopPropagation();
    }
    const dropdown = document.getElementById('userDropdown');
    const bubble = document.querySelector('.user-bubble');
    if (dropdown && bubble) {
        dropdown.classList.toggle('show');
        bubble.classList.toggle('active');
    }
}

// Close dropdown when clicking outside
document.addEventListener('click', function(event) {
    const dropdown = document.getElementById('userDropdown');
    const bubble = document.querySelector('.user-bubble');
    if (dropdown?.classList.contains('show') && 
        !bubble?.contains(event.target) && 
        !dropdown?.contains(event.target)) {
        dropdown.classList.remove('show');
        bubble?.classList.remove('active');
    }
});

async function handleMessageSubmit(event) {
    event.preventDefault();
    
    if (isSubmitting) {
        console.log('Message submission already in progress');
        return false;
    }
    
    const form = event.target;
    const content = messageInput.value.trim();
    
    if (!content) {
        console.log('Empty message content');
        return false;
    }
    
    const channelId = document.querySelector('.chat-header h2')?.getAttribute('data-channel-id');
    if (!channelId || !currentWindowId) {
        console.error('Missing channelId or currentWindowId', { channelId, currentWindowId });
        showNotification('Error: Missing channel information', 'error');
        return false;
    }
    
    isSubmitting = true;
    
    try {
        const url = `/channel/${channelId}/message?windowId=${currentWindowId}`;
        console.log('Attempting to send message:', {
            url,
            content,
            channelId,
            currentWindowId
        });
        
        const response = await fetch(url, {
            method: 'POST',
            headers: {
                'Content-Type': 'application/json',
                'Accept': 'application/json'
            },
            body: JSON.stringify({ content: content })
        });
        
        if (response.status === 401 || response.status === 403 || response.status === 404) {
            window.location.href = '/error/no-window-id';
            return false;
        }
        
        console.log('Response received:', {
            status: response.status,
            statusText: response.statusText,
            headers: Object.fromEntries(response.headers.entries())
        });
        
        const data = await response.json();
        
        if (!response.ok) {
            if (data.error && (data.error.includes('Not authenticated') || data.error.includes('Not found'))) {
                window.location.href = '/error/no-window-id';
                return false;
            }
            throw new Error(data.error || `Server error: ${response.status} ${response.statusText}`);
        }
        
        if (data.success) {
            console.log('Message sent successfully');
            messageInput.value = '';
            lastActivityTime = new Date();
            updateLastActivityTime();
            await refreshMessages();
            messageInput.focus();
        } else {
            throw new Error(data.error || 'Failed to send message');
        }
    } catch (error) {
        console.error('Error in handleMessageSubmit:', {
            error: error.message,
            stack: error.stack,
            name: error.name
        });
        if (error.message && error.message.includes('Not authenticated')) {
            window.location.href = '/error/no-window-id';
            return false;
        }
        showNotification(error.message || 'Failed to send message. Please try again.', 'error');
    } finally {
        isSubmitting = false;
    }
    
    return false;
}

async function refreshMessages() {
    if (document.hidden) return;
    
    const channelId = document.querySelector('.chat-header h2')?.getAttribute('data-channel-id');
    const windowId = new URLSearchParams(window.location.search).get('windowId');
    const messagesContainer = document.getElementById('messages');
    
    if (!channelId || !windowId || !messagesContainer) {
        console.error('Missing required elements for message refresh');
        return;
    }
    
    // Store current scroll position and height
    const previousScrollHeight = messagesContainer.scrollHeight;
    const previousScrollTop = messagesContainer.scrollTop;
    
    try {
        const response = await fetch(`/channel/${channelId}/messages?windowId=${windowId}`);
        
        if (response.status === 401 || response.status === 403 || response.status === 404) {
            window.location.href = '/error/no-window-id';
            return;
        }
        
        if (!response.ok) {
            const data = await response.json();
            if (data.error && (data.error.includes('Not authenticated') || data.error.includes('Not found'))) {
                window.location.href = '/error/no-window-id';
                return;
            }
            throw new Error(`HTTP error! status: ${response.status}`);
        }
        
        const data = await response.json();
        
        // Update active users count if available
        if (data.activeUsers !== undefined) {
            updateActiveUsersCount(data.activeUsers);
        }
        
        if (data.messages && Array.isArray(data.messages)) {
            // Clear existing messages
            messagesContainer.innerHTML = '';
            
            // Add new messages
            data.messages.forEach(message => {
                const messageElement = createMessageElement(message);
                messagesContainer.appendChild(messageElement);
            });

            // Handle scrolling based on user's position
            if (shouldAutoScroll) {
                // If we were at the bottom, scroll to the new bottom
                scrollToBottom();
            } else {
                // If user was scrolled up, maintain their relative position
                const newScrollHeight = messagesContainer.scrollHeight;
                const scrollDiff = newScrollHeight - previousScrollHeight;
                messagesContainer.scrollTop = previousScrollTop + scrollDiff;
            }
        }
        
        // Update typing indicators
        if (data.typingUsers && Array.isArray(data.typingUsers)) {
            const currentUser = document.querySelector('.user-bubble span')?.textContent;
            const otherTypingUsers = data.typingUsers.filter(user => user !== currentUser);
            
            if (otherTypingUsers.length > 0) {
                showTypingIndicator(otherTypingUsers[0] + (otherTypingUsers.length > 1 ? ' and others' : ''));
            } else {
                hideTypingIndicator();
            }
        } else {
            hideTypingIndicator();
        }
    } catch (error) {
        console.error('Error refreshing messages:', error);
    }
}

function createMessageElement(message) {
    const messageDiv = document.createElement('div');
    messageDiv.className = message.isCurrentUser ? 'message-sent' : 'message-received';
    messageDiv.setAttribute('data-message-id', message.id);

    const bubble = document.createElement('div');
    bubble.className = 'message-bubble';

    const header = document.createElement('div');
    header.className = 'message-header';

    const avatar = document.createElement('div');
    avatar.className = 'user-avatar';
    avatar.innerHTML = '<i class="fas fa-user-circle"></i>';

    const username = document.createElement('span');
    username.className = 'username';
    username.textContent = message.username;

    const timestamp = document.createElement('span');
    timestamp.className = 'timestamp';
    timestamp.textContent = new Date(message.timestamp).toLocaleTimeString('en-US', { 
        hour: '2-digit', 
        minute: '2-digit',
        hour12: false 
    });

    const content = document.createElement('div');
    content.className = 'message-content';
    content.textContent = message.content;

    header.appendChild(avatar);
    header.appendChild(username);
    header.appendChild(timestamp);
    bubble.appendChild(header);
    bubble.appendChild(content);
    messageDiv.appendChild(bubble);

    return messageDiv;
}

function handleTyping() {
    const messageInput = document.querySelector('input[name="content"]');
    const content = messageInput.value.trim();
    const username = document.querySelector('.user-bubble span').textContent;
    
    if (content && !isUserTyping) {
        isUserTyping = true;
        showTypingIndicator(username);
        notifyTypingStatus(true);
    } else if (!content && isUserTyping) {
        stopTyping();
    }
    
    clearTimeout(typingTimeout);
    typingTimeout = setTimeout(stopTyping, 2000);
}

function stopTyping() {
    if (isUserTyping) {
        isUserTyping = false;
        hideTypingIndicator();
        notifyTypingStatus(false);
    }
}

function showTypingIndicator(username) {
    const indicator = document.getElementById('typingIndicator');
    const usernameSpan = indicator.querySelector('.typing-username');
    usernameSpan.textContent = username + ' is typing';
    indicator.style.display = 'block';
}

function hideTypingIndicator() {
    const indicator = document.getElementById('typingIndicator');
    indicator.style.display = 'none';
}

function notifyTypingStatus(isTyping) {
    const channelId = document.querySelector('.chat-header h2')?.getAttribute('data-channel-id');
    if (!channelId || !currentWindowId) {
        console.error('Missing channelId or currentWindowId for typing notification', { channelId, currentWindowId });
        return;
    }
    
    fetch(`/channel/${channelId}/typing?windowId=${currentWindowId}`, {
        method: 'POST',
        headers: {
            'Content-Type': 'application/json',
        },
        body: JSON.stringify({
            isTyping: isTyping,
            username: document.querySelector('.user-bubble span')?.textContent
        })
    }).catch(error => console.error('Error updating typing status:', error));
}

function showNotification(message, type) {
    const notification = document.createElement('div');
    notification.className = `${type}-message`;
    notification.textContent = message;
    document.body.appendChild(notification);
    
    setTimeout(() => {
        notification.remove();
    }, 3000);
}

// Clean up event source when leaving the page
window.addEventListener('beforeunload', () => {
    if (eventSource) {
        eventSource.close();
    }
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

function showEditChannelModal() {
    const modal = document.getElementById('editChannelModal');
    if (modal) {
        modal.style.display = 'block';
        // Focus on the input field
        const input = modal.querySelector('#channelName');
        if (input) {
            input.focus();
            input.select();
        }
    }
}

function closeEditChannelModal() {
    const modal = document.getElementById('editChannelModal');
    if (modal) {
        modal.style.display = 'none';
    }
}

async function handleEditChannelSubmit(event) {
    event.preventDefault();
    
    const form = event.target;
    const channelName = form.querySelector('#channelName').value.trim();
    
    if (!channelName) {
        showNotification('Channel name cannot be empty', 'error');
        return false;
    }
    
    if (channelName.length < 3 || channelName.length > 30) {
        showNotification('Channel name must be between 3 and 30 characters', 'error');
        return false;
    }
    
    if (!/^[a-zA-Z0-9\s_-]+$/.test(channelName)) {
        showNotification('Channel name can only contain letters, numbers, spaces, underscores, and hyphens', 'error');
        return false;
    }
    
    try {
        const formData = new FormData(form);
        const response = await fetch(form.action, {
            method: 'POST',
            body: formData
        });
        
        if (response.status === 401 || response.status === 403 || response.status === 404) {
            // Handle authentication and not found errors
            window.location.href = '/error/no-window-id';
            return false;
        }
        
        if (!response.ok) {
            const data = await response.json();
            if (data.error && (data.error.includes('Not authenticated') || data.error.includes('Not found'))) {
                window.location.href = '/error/no-window-id';
                return false;
            }
            throw new Error(data.error || `Server error: ${response.status} ${response.statusText}`);
        }
        
        // Close the modal
        closeEditChannelModal();
        
        // Show success message
        showNotification('Channel name updated successfully', 'success');
        
        // Refresh the page to show updated channel name
        window.location.reload();
        
    } catch (error) {
        console.error('Error updating channel:', error);
        if (error.message && error.message.includes('Not authenticated')) {
            window.location.href = '/error/no-window-id';
            return false;
        }
        showNotification(error.message || 'Failed to update channel name', 'error');
    }
    
    return false;
} 