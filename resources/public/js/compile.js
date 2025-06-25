document.addEventListener('DOMContentLoaded', function() {
    const form = document.querySelector('#compile-form');
    const eyeballForm = document.querySelector('#eyeball-form');
    const outputContainer = document.getElementById('output-container');
    const additionalOutputs = document.getElementById('additional-outputs');
    const warningsDiv = document.getElementById('warnings');
    const notesDiv = document.getElementById('notes');
    const errorDiv = document.getElementById('error');
    const headerButton = document.getElementById('get-header');
    const headerOutput = document.getElementById('header-output');

    // Eyeball form elements
    const eyeballOutput = document.getElementById('eyeball-output');
    const eyeballWarnings = document.getElementById('eyeball-warnings');
    const eyeballNotes = document.getElementById('eyeball-notes-text');

    // Function to detect and pretty-print JSON
    function formatOutput(content) {
        if (typeof content !== 'string') {
            return content;
        }
        
        // Trim whitespace to check if it looks like JSON
        const trimmed = content.trim();
        
        // Check if it starts with { or [ and ends with } or ]
        if ((trimmed.startsWith('{') && trimmed.endsWith('}')) || 
            (trimmed.startsWith('[') && trimmed.endsWith(']'))) {
            try {
                // Try to parse as JSON
                const parsed = JSON.parse(trimmed);
                // Pretty-print with 2-space indentation
                return JSON.stringify(parsed, null, 2);
            } catch (e) {
                // If parsing fails, it's not valid JSON, return original
                return content;
            }
        }
        
        // Not JSON, return original content
        return content;
    }

    // Function to copy text to clipboard
    async function copyToClipboard(text) {
        try {
            await navigator.clipboard.writeText(text);
            return true;
        } catch (err) {
            // Fallback for older browsers
            const textArea = document.createElement('textarea');
            textArea.value = text;
            document.body.appendChild(textArea);
            textArea.select();
            try {
                document.execCommand('copy');
                document.body.removeChild(textArea);
                return true;
            } catch (fallbackErr) {
                document.body.removeChild(textArea);
                return false;
            }
        }
    }

    // Function to create a copy button
    function createCopyButton(outputElement) {
        const copyButton = document.createElement('button');
        copyButton.className = 'copy-button';
        copyButton.textContent = 'Copy';
        copyButton.title = 'Copy to clipboard';
        
        copyButton.addEventListener('click', async function() {
            const textToCopy = outputElement.textContent;
            const success = await copyToClipboard(textToCopy);
            
            if (success) {
                copyButton.textContent = 'Copied!';
                copyButton.classList.add('copied');
                setTimeout(() => {
                    copyButton.textContent = 'Copy';
                    copyButton.classList.remove('copied');
                }, 2000);
            } else {
                copyButton.textContent = 'Failed';
                setTimeout(() => {
                    copyButton.textContent = 'Copy';
                }, 2000);
            }
        });
        
        return copyButton;
    }

    // Function to initialize copy buttons for existing elements
    function initializeCopyButtons() {
        // Initialize copy button for main output
        const mainOutput = document.getElementById('output-0');
        const mainCopyButton = mainOutput.parentElement.querySelector('.copy-button');
        if (mainCopyButton) {
            mainCopyButton.addEventListener('click', async function() {
                const textToCopy = mainOutput.textContent;
                const success = await copyToClipboard(textToCopy);
                
                if (success) {
                    mainCopyButton.textContent = 'Copied!';
                    mainCopyButton.classList.add('copied');
                    setTimeout(() => {
                        mainCopyButton.textContent = 'Copy';
                        mainCopyButton.classList.remove('copied');
                    }, 2000);
                } else {
                    mainCopyButton.textContent = 'Failed';
                    setTimeout(() => {
                        mainCopyButton.textContent = 'Copy';
                    }, 2000);
                }
            });
        }

        // Initialize copy button for header output
        const headerOutput = document.getElementById('header-output');
        const headerCopyButton = headerOutput.parentElement.querySelector('.copy-button');
        if (headerCopyButton) {
            headerCopyButton.addEventListener('click', async function() {
                const textToCopy = headerOutput.textContent;
                const success = await copyToClipboard(textToCopy);
                
                if (success) {
                    headerCopyButton.textContent = 'Copied!';
                    headerCopyButton.classList.add('copied');
                    setTimeout(() => {
                        headerCopyButton.textContent = 'Copy';
                        headerCopyButton.classList.remove('copied');
                    }, 2000);
                } else {
                    headerCopyButton.textContent = 'Failed';
                    setTimeout(() => {
                        headerCopyButton.textContent = 'Copy';
                    }, 2000);
                }
            });
        }

        // Initialize copy button for eyeball output
        const eyeballOutput = document.getElementById('eyeball-output');
        const eyeballCopyButton = eyeballOutput.parentElement.querySelector('.copy-button');
        if (eyeballCopyButton) {
            eyeballCopyButton.addEventListener('click', async function() {
                const textToCopy = eyeballOutput.textContent;
                const success = await copyToClipboard(textToCopy);
                
                if (success) {
                    eyeballCopyButton.textContent = 'Copied!';
                    eyeballCopyButton.classList.add('copied');
                    setTimeout(() => {
                        eyeballCopyButton.textContent = 'Copy';
                        eyeballCopyButton.classList.remove('copied');
                    }, 2000);
                } else {
                    eyeballCopyButton.textContent = 'Failed';
                    setTimeout(() => {
                        eyeballCopyButton.textContent = 'Copy';
                    }, 2000);
                }
            });
        }
    }

    // Initialize copy buttons when page loads
    initializeCopyButtons();

    // Function to clear all messages
    function clearMessages() {
        warningsDiv.style.display = 'none';
        notesDiv.style.display = 'none';
        errorDiv.style.display = 'none';
        warningsDiv.textContent = '';
        notesDiv.textContent = '';
        errorDiv.textContent = '';
    }

    // Function to clear eyeball messages
    function clearEyeballMessages() {
        eyeballWarnings.style.display = 'none';
        eyeballNotes.style.display = 'none';
        eyeballWarnings.textContent = '';
        eyeballNotes.textContent = '';
    }

    // Function to clear all outputs
    function clearOutputs() {
        const outputContainer = document.getElementById('output-container');
        const additionalOutputs = document.getElementById('additional-outputs');
        const mainOutput = document.getElementById('output-0');
        
        // Clear main output
        mainOutput.textContent = 'Compiled output will appear here...';
        
        // Remove all additional outputs
        while (additionalOutputs.firstChild) {
            additionalOutputs.removeChild(additionalOutputs.firstChild);
        }
    }

    // Function to create a new output area
    function createOutputArea(index, content) {
        const outputGroup = document.createElement('div');
        outputGroup.className = 'output-group';
        
        const label = document.createElement('label');
        label.setAttribute('for', `output-${index}`);
        label.textContent = `Output ${index + 1}:`;
        
        const pre = document.createElement('pre');
        pre.className = 'output-area';
        
        const code = document.createElement('code');
        code.id = `output-${index}`;
        code.className = 'language-clojure';
        code.textContent = formatOutput(content);
        
        const copyButton = createCopyButton(code);
        
        pre.appendChild(code);
        pre.appendChild(copyButton);
        outputGroup.appendChild(label);
        outputGroup.appendChild(pre);
        
        return outputGroup;
    }

    // Function to show messages
    function showMessages(data) {
        clearMessages();
        
        if (data.warnings && data.warnings.length > 0) {
            warningsDiv.textContent = 'Warnings:\n' + data.warnings.join('\n');
            warningsDiv.style.display = 'block';
        }
        
        if (data.notes) {
            notesDiv.textContent = 'Notes:\n' + data.notes;
            notesDiv.style.display = 'block';
        }
        
        if (data.error) {
            errorDiv.textContent = 'Error: ' + data.error;
            errorDiv.style.display = 'block';
        }
    }

    // Function to show eyeball messages
    function showEyeballMessages(data) {
        clearEyeballMessages();
        
        if (data.issues && data.issues.length > 0) {
            eyeballWarnings.textContent = 'Issues:\n' + data.issues.join('\n');
            eyeballWarnings.style.display = 'block';
        }
        
        if (data.notes) {
            eyeballNotes.textContent = 'Notes:\n' + data.notes;
            eyeballNotes.style.display = 'block';
        }
    }

    // Function to show error messages
    function showError(message) {
        errorDiv.textContent = 'Error: ' + message;
        errorDiv.style.display = 'block';
    }

    // Function to show notes
    function showNotes(notes) {
        notesDiv.textContent = 'Notes:\n' + notes;
        notesDiv.style.display = 'block';
    }

    // Function to show warnings
    function showWarnings(warnings) {
        warningsDiv.textContent = 'Warnings:\n' + warnings.join('\n');
        warningsDiv.style.display = 'block';
    }

    // Handle compile form submission
    if (form) {
        form.addEventListener('submit', async function(e) {
            e.preventDefault();
            
            clearMessages();
            clearOutputs();
            
            const formData = new FormData(form);
            
            try {
                const response = await fetch(form.action, {
                    method: 'POST',
                    headers: {
                        'Content-Type': 'application/json'
                    },
                    body: JSON.stringify({
                        target: formData.get('target'),
                        dsl: formData.get('dsl')
                    })
                });
                
                const data = await response.json();
                
                if (data.error) {
                    showError(data.error);
                    return;
                }
                
                if (data.warnings && data.warnings.length > 0) {
                    showWarnings(data.warnings);
                }
                
                if (data.notes && data.notes.length > 0) {
                    showNotes(data.notes);
                }
                
                if (data.code) {
                    const mainOutput = document.getElementById('output-0');
                    const additionalOutputs = document.getElementById('additional-outputs');
                    
                    if (Array.isArray(data.code)) {
                        // Handle multiple outputs
                        data.code.forEach((code, index) => {
                            if (index === 0) {
                                // First output goes in the main area
                                mainOutput.textContent = formatOutput(code);
                            } else {
                                // Additional outputs get their own areas
                                const outputArea = createOutputArea(index, code);
                                additionalOutputs.appendChild(outputArea);
                            }
                        });
                    } else {
                        // Handle single output
                        mainOutput.textContent = formatOutput(data.code);
                    }
                }
            } catch (error) {
                showError('Failed to compile: ' + error.message);
            }
        });
    }

    // Handle eyeball form submission
    if (eyeballForm) {
        eyeballForm.addEventListener('submit', async function(e) {
            e.preventDefault();
            
            clearEyeballMessages();
            
            const formData = new FormData(eyeballForm);
            const dslName = window.location.pathname.split('/').pop(); // Get DSL name from URL
            
            try {
                eyeballOutput.textContent = 'Validating code...';
                
                const response = await fetch(`/eyeball/${dslName}`, {
                    method: 'POST',
                    headers: {
                        'Content-Type': 'application/json'
                    },
                    body: JSON.stringify({
                        target: formData.get('target'),
                        code: formData.get('code')
                    })
                });
                
                const data = await response.json();
                
                // Display the result with JSON formatting
                eyeballOutput.textContent = formatOutput(JSON.stringify(data, null, 2));
                
                // Show any messages
                showEyeballMessages(data);
                
            } catch (error) {
                eyeballOutput.textContent = 'Error: ' + error.message;
            }
        });
    }

    // Handle header request
    if (headerButton) {
        headerButton.addEventListener('click', async function() {
            const target = document.getElementById('target').value;
            const dslName = window.location.pathname.split('/').pop(); // Get DSL name from URL
            
            console.log('DEBUG: Requesting header for DSL:', dslName, 'target:', target);
            
            try {
                headerOutput.textContent = 'Loading header...';
                
                const response = await fetch(`/header/${dslName}/${target}`);
                console.log('DEBUG: Header response status:', response.status);
                
                const data = await response.json();
                console.log('DEBUG: Header response data:', data);
                
                if (data.error) {
                    console.error('DEBUG: Header error:', data.error);
                    headerOutput.textContent = 'Error: ' + data.error;
                    return;
                }
                
                if (data.code) {
                    console.log('DEBUG: Header code found:', data.code);
                    headerOutput.textContent = formatOutput(data.code);
                } else {
                    console.warn('DEBUG: No header code in response');
                    headerOutput.textContent = 'No header available';
                }
            } catch (error) {
                console.error('DEBUG: Header request failed:', error);
                headerOutput.textContent = 'Error: ' + error.message;
            }
        });
    }
});  