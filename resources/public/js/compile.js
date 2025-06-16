document.addEventListener('DOMContentLoaded', function() {
    const form = document.querySelector('form');
    const outputContainer = document.getElementById('output-container');
    const additionalOutputs = document.getElementById('additional-outputs');
    const warningsDiv = document.getElementById('warnings');
    const notesDiv = document.getElementById('notes');
    const errorDiv = document.getElementById('error');
    const headerButton = document.getElementById('get-header');
    const headerOutput = document.getElementById('header-output');

    // Function to clear all messages
    function clearMessages() {
        warningsDiv.style.display = 'none';
        notesDiv.style.display = 'none';
        errorDiv.style.display = 'none';
        warningsDiv.textContent = '';
        notesDiv.textContent = '';
        errorDiv.textContent = '';
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
        code.textContent = content;
        
        pre.appendChild(code);
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

    // Handle form submission
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
                                mainOutput.textContent = code;
                            } else {
                                // Additional outputs get their own areas
                                const outputArea = createOutputArea(index, code);
                                additionalOutputs.appendChild(outputArea);
                            }
                        });
                    } else {
                        // Handle single output
                        mainOutput.textContent = data.code;
                    }
                }
            } catch (error) {
                showError('Failed to compile: ' + error.message);
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
                    headerOutput.textContent = data.code;
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