import clsx from 'clsx';
import type { ButtonHTMLAttributes, ReactNode } from 'react';
import { Icon, type IconName } from './Icon';
import { Spinner } from './Spinner';

type Variant = 'primary' | 'secondary' | 'ghost' | 'danger' | 'leaf';
type Size = 'xs' | 'sm' | 'md';

const VARIANTS: Record<Variant, string> = {
  primary: 'bg-accent text-accent-ink hover:bg-accent-hover shadow-sm',
  secondary: 'bg-surface text-ink border border-line-strong hover:bg-surface-2 shadow-sm',
  ghost: 'text-ink-2 hover:bg-surface-3 hover:text-ink',
  danger: 'bg-danger-soft text-danger-ink border border-danger/40 hover:bg-danger hover:text-white',
  leaf: 'bg-leaf text-white hover:brightness-110 shadow-sm dark:text-[#10200a]',
};

const SIZES: Record<Size, string> = {
  xs: 'h-6 px-2 text-[11px] gap-1 rounded-md',
  sm: 'h-8 px-2.5 text-xs gap-1.5 rounded-lg',
  md: 'h-9 px-3.5 text-sm gap-2 rounded-lg',
};

/** v0.0.4 🍊 Props of the shared button. */
export interface ButtonProps extends ButtonHTMLAttributes<HTMLButtonElement> {
  variant?: Variant;
  size?: Size;
  icon?: IconName;
  loading?: boolean;
  children?: ReactNode;
}

/** v0.0.4 🍊 Button with citrus variants, optional icon and a loading spinner. */
export function Button({
  variant = 'secondary',
  size = 'md',
  icon,
  loading = false,
  disabled,
  className,
  children,
  type = 'button',
  ...rest
}: ButtonProps) {
  return (
    <button
      type={type}
      disabled={disabled || loading}
      className={clsx(
        'inline-flex shrink-0 items-center justify-center font-medium whitespace-nowrap transition-colors',
        'disabled:cursor-not-allowed disabled:opacity-50',
        VARIANTS[variant],
        SIZES[size],
        className,
      )}
      {...rest}
    >
      {loading ? <Spinner size={size === 'md' ? 16 : 13} /> : icon ? <Icon name={icon} size={size === 'md' ? 16 : 14} /> : null}
      {children}
    </button>
  );
}

/** v0.0.4 🍊 Square icon-only button with an accessible label. */
export function IconButton({
  icon,
  label,
  size = 'sm',
  variant = 'ghost',
  className,
  ...rest
}: Omit<ButtonProps, 'children' | 'icon'> & { icon: IconName; label: string }) {
  return (
    <Button
      aria-label={label}
      title={label}
      icon={icon}
      size={size}
      variant={variant}
      className={clsx(size === 'xs' ? 'w-6 px-0' : size === 'sm' ? 'w-8 px-0' : 'w-9 px-0', className)}
      {...rest}
    />
  );
}
