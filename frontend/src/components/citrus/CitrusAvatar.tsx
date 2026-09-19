import clsx from 'clsx';
import { memo } from 'react';
import { CitrusBody, type CitrusExpression } from './CitrusBody';

/** v0.0.4 🍊 Static round citrus face used in chat, tickets, lists and pickers. */
export const CitrusAvatar = memo(function CitrusAvatar({
  avatarKey,
  color,
  size = 28,
  title,
  expression = 'happy',
  dimmed = false,
  className,
}: {
  avatarKey: string;
  color: string;
  size?: number;
  title?: string;
  expression?: CitrusExpression;
  dimmed?: boolean;
  className?: string;
}) {
  return (
    <svg
      width={size}
      height={size}
      viewBox="0 0 64 64"
      overflow="visible"
      role={title ? 'img' : undefined}
      aria-label={title}
      aria-hidden={title ? undefined : true}
      className={clsx('shrink-0', dimmed && 'opacity-60 grayscale', className)}
    >
      {title ? <title>{title}</title> : null}
      <CitrusBody cx={32} cy={37} r={21} color={color} avatarKey={avatarKey} expression={expression} />
    </svg>
  );
});
