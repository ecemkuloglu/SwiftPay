import js from '@eslint/js';
import prettier from 'eslint-config-prettier';
import reactHooks from 'eslint-plugin-react-hooks';
import reactRefresh from 'eslint-plugin-react-refresh';
import globals from 'globals';
import tseslint from 'typescript-eslint';

export default tseslint.config(
  { ignores: ['dist', 'node_modules', 'coverage'] },
  {
    extends: [js.configs.recommended, ...tseslint.configs.recommended],
    files: ['**/*.{ts,tsx,js}'],
    languageOptions: { ecmaVersion: 2022, globals: { ...globals.browser, ...globals.node } },
    plugins: { 'react-hooks': reactHooks, 'react-refresh': reactRefresh },
    rules: {
      ...reactHooks.configs.recommended.rules,
      'react-refresh/only-export-components': ['warn', { allowConstantExport: true }],
      // Security / correctness rules for this app
      'no-console': 'error', // never log tokens, IBANs or response bodies
      'no-restricted-globals': [
        'error',
        { name: 'localStorage', message: 'Tokens and data must not go to localStorage.' },
        { name: 'parseFloat', message: 'Money is a string; never parseFloat it.' },
      ],
      'no-restricted-properties': [
        'error',
        { object: 'window', property: 'localStorage', message: 'Use sessionStorage via tokenStorage.' },
        { object: 'Number', property: 'parseFloat', message: 'Money is a string; never parseFloat it.' },
      ],
      'no-restricted-syntax': [
        'error',
        {
          selector: "JSXAttribute[name.name='dangerouslySetInnerHTML']",
          message: 'Render user data as text only.',
        },
      ],
    },
  },
  prettier,
);
